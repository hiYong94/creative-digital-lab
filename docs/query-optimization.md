# 쿼리 최적화 및 트러블슈팅

핫패스 쿼리 분석, 인덱스 설계 근거, 비동기 처리 설계, 장애 대응 시나리오를 기술합니다.

---

## 핫패스 쿼리 분석

### 쿼리 1 — 타임라인 복원

`GET /sessions/{id}/timeline?at=`에서 항상 실행되는 가장 빈번하고 비용이 큰 쿼리입니다.

```sql
SELECT id, session_id, user_id, client_event_id, type, payload, sequence_no, server_received_at
FROM chat_events
WHERE session_id = ?         -- BINARY(16) 동등 비교
  AND server_received_at <= ? -- 범위 조건
ORDER BY server_received_at ASC, sequence_no ASC;
```

**인덱스**: `idx_events_session_time (session_id, server_received_at)`

| 단계 | 처리 | 비용 |
|------|------|------|
| `session_id` 동등 필터 | 인덱스 첫 컬럼으로 해당 세션 행 추출 | O(log N) |
| `server_received_at` 범위 스캔 | 인덱스 두 번째 컬럼으로 범위 내 행 순회 | O(K), K = 범위 내 이벤트 수 |
| ORDER BY | 인덱스 순서와 일치 → 추가 정렬 비용 없음 | O(1) |

**예상 병목**: 이벤트가 수만 건 이상 쌓인 세션에서 `at`이 현재에 가까울수록 읽는 행 수가 많아집니다.

**개선 방향**: Snapshot+Delta 전략이 이 병목을 원천 차단합니다. Snapshot이 있으면 아래 쿼리로 교체됩니다.

```sql
-- Delta 이벤트만 조회 (최대 interval-1 = 49건)
SELECT ... FROM chat_events
WHERE session_id = ?
  AND sequence_no > ?          -- last_sequence_no (Snapshot 기준점)
  AND server_received_at <= ?  -- at
ORDER BY server_received_at ASC, sequence_no ASC;
```

---

### 쿼리 2 — 최신 Snapshot 조회

타임라인 복원 전 항상 선행 실행됩니다. 이 쿼리가 느리면 전체 복원이 느려집니다.

```sql
SELECT id, session_id, last_event_id, state, last_sequence_no, created_at
FROM snapshots
WHERE session_id = ?
ORDER BY created_at DESC
LIMIT 1;
```

**인덱스**: `idx_snapshots_session_time (session_id, created_at DESC)`

| 단계 | 처리 | 비용 |
|------|------|------|
| `session_id` 동등 필터 | 인덱스 첫 컬럼으로 해당 세션 Snapshot 추출 | O(log N) |
| `created_at DESC` LIMIT 1 | 복합 인덱스 첫 행이 최신 Snapshot → 1회 읽기 | O(1) |

**예상 병목**: Snapshot 수가 폭발적으로 늘지 않는 이상 성능이 일정합니다. 세션 수가 매우 많아지면 인덱스 크기가 커질 수 있습니다.

**개선 방향**: 종료된 세션(`status = 'ENDED'`)의 오래된 Snapshot을 아카이빙(별도 테이블 또는 오브젝트 스토리지)하면 인덱스 크기를 제어할 수 있습니다.

---

### 쿼리 3 — 재연결 시 누락 이벤트 조회

WebSocket 재연결 시 레이턴시에 민감한 쿼리입니다.

```sql
SELECT id, session_id, user_id, client_event_id, type, payload, sequence_no, server_received_at
FROM chat_events
WHERE session_id = ?
  AND sequence_no > ?   -- resumeFromSequenceNo
ORDER BY server_received_at ASC, sequence_no ASC;
```

**인덱스**: `idx_events_session_sequence (session_id, sequence_no)`

| 단계 | 처리 | 비용 |
|------|------|------|
| `session_id` + `sequence_no >` 범위 | 두 컬럼 복합 인덱스로 누락 이벤트 구간 직접 탐색 | O(log N + K) |
| ORDER BY | `server_received_at` 기준 정렬이 인덱스와 달라 filesort 발생 | O(K log K) |

**예상 병목**: 누락 이벤트 수(K)가 많을수록 filesort 비용이 증가합니다. 1,000건 초과 시 마지막 100건만 전송하는 제한으로 최악 케이스를 방어합니다.

**개선 방향**: ORDER BY 컬럼을 `sequence_no`로 통일하면 filesort 없이 인덱스 순서를 바로 사용할 수 있습니다. 단, 정렬 기준을 변경하면 `server_received_at` 기반 순서와 불일치가 발생할 수 있으므로 트레이드오프를 검토해야 합니다.

---

## 비동기 처리 설계

### Snapshot 생성 비동기 구조

```
이벤트 수집 트랜잭션 커밋
    └─ EventCollectedEvent 발행 (Spring ApplicationEvent)
        └─ @TransactionalEventListener(AFTER_COMMIT)
            └─ @Async("snapshotTaskExecutor") — ThreadPoolTaskExecutor
                └─ count % interval == 0 → createSnapshot()
```

이벤트 수집 HTTP 응답과 Snapshot 생성이 완전히 분리됩니다. Snapshot 생성이 수 초가 걸려도 이벤트 수집 응답 시간에 영향을 주지 않습니다.

### Idempotency

동일 이벤트 count에서 리스너가 2번 실행되더라도 동일한 상태를 기반으로 Snapshot을 생성합니다. Snapshot 행이 중복 추가되지만 타임라인 복원 시 최신 1개만 사용하므로 정합성에 영향이 없습니다.

### 중복 실행 방지

현재는 `@Async`로 분리된 단일 스레드 풀에서 순차 실행됩니다. 분산 환경에서 여러 노드가 동시에 Snapshot을 생성하는 경우, Redis 분산 락(`SET NX PX`)으로 중복 실행을 방지할 수 있습니다.

```
SET snapshot_lock:{sessionId} 1 NX PX 5000
    → 획득 성공 → Snapshot 생성 → 락 해제
    → 획득 실패 → 다른 노드가 생성 중 → skip
```

### 재시도 설계

현재: 실패 시 WARN 로그 + 다음 이벤트 수집 시 자연 재시도.

프로덕션 확장 시:
```
1차 실패 → 1초 후 재시도
2차 실패 → 2초 후 재시도
3차 실패 → 4초 후 재시도
최종 실패 → DLQ(Redis List: snapshot_dlq) 적재 + ERROR 로그
```

DLQ 적재 후 운영자가 `snapshot_dlq`를 소비해 수동 재처리하거나, 별도 워커가 주기적으로 재시도합니다.

---

## 대량 데이터 조회 성능 전략

### 페이지네이션

세션 목록 조회(`GET /sessions`)는 `page`/`size` 파라미터로 오프셋 페이지네이션을 제공합니다. 기본 20건, 최대 100건입니다.

대규모 데이터에서 오프셋 페이지네이션은 OFFSET이 커질수록 성능이 저하됩니다. 이 경우 커서 기반 페이지네이션(`WHERE created_at < ? ORDER BY created_at DESC LIMIT ?`)으로 전환하면 항상 O(log N) 성능을 유지할 수 있습니다.

### 이벤트 아카이빙

종료된 세션(`status = 'ENDED'`)의 오래된 이벤트는 핫 데이터가 아닙니다. 일정 기간이 지난 ENDED 세션의 이벤트를 콜드 스토리지(S3 등)로 아카이빙하고 `chat_events` 테이블에서 삭제하면 조회 성능을 유지할 수 있습니다. 단, Append-only 원칙을 유지하면서 아카이빙 전 Snapshot을 반드시 생성해야 합니다.

### 커버링 인덱스

타임라인 복원 쿼리의 SELECT 컬럼을 인덱스 컬럼으로 제한하면 테이블 행을 읽지 않고 인덱스만으로 결과를 반환할 수 있습니다(커버링 인덱스). 현재는 `payload(JSON)` 컬럼이 포함되어 커버링 인덱스 적용이 어렵습니다. 조회 빈도가 높아지면 `payload`를 별도 테이블로 분리하는 방안을 검토할 수 있습니다.
