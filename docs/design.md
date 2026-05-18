# 설계 문서

재연결 데이터 정합성, 중복 이벤트 처리, 서버 확장성, 관측 가능성, 비동기 처리 구조, 장애 대응 시나리오를 기술합니다.

---

## 재연결 데이터 정합성

### 핵심 원칙

이벤트 로그(`chat_events`)가 Append-only 불변 구조이므로, 연결이 끊겼다 다시 연결되어도 DB의 이벤트 기록은 손상되지 않습니다. 클라이언트가 마지막으로 받은 `sequenceNo`만 알면 누락 구간을 정확히 특정할 수 있습니다.

### 흐름

```
1. 연결 끊김 (비정상)
   └─ 서버: SessionDisconnectEvent 감지
       ├─ DISCONNECT 이벤트 → chat_events 저장 (sequenceNo = countBySessionId + 1)
       └─ PresenceStore에서 해당 userId 키 삭제

2. 재연결
   └─ 클라이언트: STOMP CONNECT 후 아래 순서로 전송
       ├─ SUBSCRIBE /user/queue/missed
       └─ SEND /app/sessions/{id}/reconnect
              { userId, clientEventId, resumeFromSequenceNo: N }

3. 서버 처리
   ├─ RECONNECT 이벤트 → chat_events 저장
   ├─ findMissed(sequenceNo > N) 조회
   └─ /user/queue/missed 로 누락 이벤트 전송
```

### sequenceNo 연속성 보장

서버 발급 이벤트(DISCONNECT, RECONNECT)의 `sequenceNo`는 `countBySessionId + 1`로 채번합니다. 클라이언트 발급 이벤트(1, 2, 3...)와 단위가 통일되어 `resumeFromSequenceNo` 기반 누락 구간 판별이 정확합니다.

### 1,000개 초과 폴백

```
missed.size() > 1000
  └─ 마지막 100개만 /user/queue/missed 전송 + WARN 로그
     클라이언트: GET /sessions/{id}/timeline?at=now 로 전체 상태 동기화
```

이벤트 로그가 DB에 보존되어 있으므로 timeline API로 언제든 정확한 상태를 복원할 수 있습니다.

---

## 중복 이벤트 처리

### 이중 방어 구조

네트워크 불안정으로 클라이언트가 동일 이벤트를 재전송할 경우 두 단계로 차단합니다.

```
클라이언트 요청
    │
    ▼
[1차] Redis — IdempotencyAspect
    ├─ clientEventId 키 존재 → DB 조회 후 기존 이벤트 즉시 반환 (proceed 미호출)
    ├─ 키 없음 → proceed() 통과
    └─ Redis 장애 → WARN 로그 후 bypass (2차 방어로 위임)
    │
    ▼
[2차] MySQL — INSERT IGNORE
    ├─ UNIQUE(session_id, client_event_id) 위반 → INSERT IGNORE로 silent skip
    └─ affected=0 → 기존 행 조회 후 200 반환
    │
    ▼
[저장 성공] Redis에 clientEventId 키 저장 (TTL 10분)
```

### Projection 레벨 방어

타임라인 복원(`applyEvents()`) 중에도 `appliedSequenceNos` Set으로 동일 `sequenceNo` 이벤트를 건너뜁니다. DB 저장 레벨의 중복 차단이 선행되므로 이는 Snapshot+Delta 경계 처리를 위한 추가 방어입니다.

### 순서 뒤바뀜 처리

DB 조회 시 `ORDER BY server_received_at ASC, sequence_no ASC`로 정렬합니다. 클라이언트 시계(`client_sent_at`)는 기기마다 편차가 있으므로 정렬 기준으로 사용하지 않습니다.

| 우선순위 | 기준 | 이유 |
|---|---|---|
| 1 | `server_received_at` | 서버가 채번 — 클라이언트 시계 편차 무관 |
| 2 | `sequence_no` | 동일 마이크로초 내 tie-break |

---

## 서버 수평 확장 전략

### 현재 구조 (단일 노드)

Simple Broker는 JVM 내부 메모리에서 동작합니다. 단일 노드에서는 문제없지만 노드를 추가하면 각 노드의 브로커가 분리되어 메시지가 누락됩니다.

### WebSocket 브로커 확장 — RabbitMQ STOMP Relay

```
노드 A ─┐
노드 B ─┼──▶ RabbitMQ STOMP Relay ◀── 모든 클라이언트 구독
노드 C ─┘
```

Spring의 `StompBrokerRelayMessageHandler`가 RabbitMQ와의 연결을 관리합니다. 코드 변경은 `WebSocketConfig` 한 곳입니다.

```java
// 변경 전: Simple Broker
registry.enableSimpleBroker("/topic", "/queue");

// 변경 후: RabbitMQ STOMP Relay
registry.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost("rabbitmq-host")
        .setRelayPort(61613);
```

### Read Replica 라우팅

읽기 트래픽(타임라인 복원, 이벤트 조회)이 증가할 경우 `@Transactional(readOnly = true)` 메서드를 Read Replica로 자동 라우팅할 수 있습니다.

```
쓰기 요청 (@Transactional)           → Primary DB
읽기 요청 (@Transactional(readOnly)) → Read Replica
```

`DataSourceRoutingAspect` + `AbstractRoutingDataSource` 패턴으로 Application Service 코드 변경 없이 라우팅을 추가할 수 있습니다. 현재는 단일 DataSource를 사용하며, `@Transactional(readOnly = true)` 어노테이션만 명시해 전환 준비를 완료했습니다.

---

## 관측 가능성

### MDC (Mapped Diagnostic Context)

`@UseCase` AOP가 Application Service 메서드 진입 시 자동으로 MDC를 주입합니다.

| MDC 키 | 값 | 용도 |
|---|---|---|
| `traceId` | UUID (요청당 1개) | 로그-에러응답 연계 |
| `sessionId` | 처리 중인 세션 ID | 세션별 로그 필터링 |
| `useCase` | 메서드 설명 ("이벤트 수집" 등) | 유즈케이스별 로그 집계 |

에러 응답 바디의 `traceId` 필드와 동일한 값이므로, 클라이언트가 전달한 `traceId`로 서버 로그를 즉시 추적할 수 있습니다.

### 로그 레벨 기준

| 레벨 | 사용 시점 | 예시 |
|---|---|---|
| `INFO` | 정상 비즈니스 이벤트 | 세션 생성, 이벤트 수집 성공, Snapshot 생성 |
| `WARN` | 복구 가능한 이상 | Redis 장애, Snapshot 실패, 중복 이벤트 차단, 누락 이벤트 1000건 초과 |
| `ERROR` | 복구 불가 예외 | 예상치 못한 런타임 오류 |
| `DEBUG` | 개발 환경 전용 | JPA 쿼리, 상세 상태 |

Service 계층에 직접 로그를 작성하지 않고 `UseCaseLoggingAspect`에서 일괄 처리합니다.

### Micrometer 메트릭

| 타입 | 메트릭 명 | 태그 |
|---|---|---|
| Counter | `event.collected` | `type`, `status` |
| Counter | `event.duplicate.blocked` | — |
| Timer | `event.collect.duration` | `status` |
| Timer | `timeline.restore.duration` | `strategy` (FULL_REPLAY / SNAPSHOT_PLUS_REPLAY) |
| Gauge | `session.active.count` | — |
| Gauge | `websocket.connections` | — |

`/actuator/health`에서 DB, Redis 커넥션 상태를 확인할 수 있습니다.

### 분산 추적 확장

현재는 단일 서비스이므로 MDC `traceId`로 충분합니다. 다중 서비스 환경으로 확장 시 Micrometer Tracing + Zipkin/Jaeger를 연동하면 서비스 간 요청 흐름을 추적할 수 있습니다.

---

## 비동기 처리 구조

### Snapshot 생성 파이프라인

```
이벤트 수집 (HTTP 요청)
    │
    ▼ @Transactional
EventApplicationService.collect()
    │ DB 저장 완료 → 트랜잭션 커밋
    │
    ▼ @TransactionalEventListener(AFTER_COMMIT)
EventCollectedEvent 발행
    │
    ▼ @Async("snapshotTaskExecutor")
SnapshotEventListener.onEventCollected()
    └─ count % 50 == 0 → createSnapshot()
```

`AFTER_COMMIT`을 사용하는 이유: 커밋 전 이벤트를 처리하면 롤백된 트랜잭션의 이벤트가 Snapshot에 포함될 수 있습니다.

### 스레드 풀

`SimpleAsyncTaskExecutor` 대신 `ThreadPoolTaskExecutor`(이름: `snapshotTaskExecutor`)를 사용합니다. 스레드 무제한 생성으로 인한 OOM을 방지하며, 풀 크기와 큐 용량은 `application.yml`에서 외부화합니다.

### Idempotency (비동기 중복 실행 방지)

`count % interval == 0` 조건은 동일 count에서 리스너가 2번 실행되어도 동일한 상태를 기반으로 Snapshot을 생성합니다. 여러 번 실행되면 Snapshot 행이 중복 추가되지만, 타임라인 복원 시 최신 Snapshot 1개만 사용하므로 정합성에는 영향이 없습니다.

### 재시도 / DLQ

현재는 Snapshot 생성 실패 시 WARN 로그만 남깁니다. **다음 이벤트 수집 시 자연 재시도** 효과가 있습니다(50번째 실패 → 100번째 재시도).

프로덕션 확장 시:
- Spring Retry + 지수 백오프(1s → 2s → 4s)로 일시적 장애 대응
- 최종 실패 이벤트를 DLQ(Redis List 또는 RabbitMQ DLX)에 적재해 수동 재처리

---

## 장애 대응 시나리오

### 1. 서버 다운 (인스턴스 장애)

**감지**
- 로드 밸런서 헬스체크(`/actuator/health`) 실패 → 해당 인스턴스 트래픽 차단
- WebSocket 클라이언트: 소켓 에러 수신

**완화**
- 로드 밸런서가 정상 인스턴스로 HTTP 트래픽 자동 라우팅
- 단일 노드(Simple Broker): 해당 인스턴스 WebSocket 세션 전부 끊어짐 → 클라이언트 재연결 필요
- 다중 노드(RabbitMQ Relay): 구독 정보가 브로커에서 유지되어 재연결 후 즉시 복구

**복구**
- 클라이언트: 재연결 + `resumeFromSequenceNo` 전달 → 누락 이벤트 수신
- `chat_events`는 MySQL에 영속되므로 인스턴스 재기동 후에도 데이터 손실 없음

---

### 2. DB 장애 또는 성능 저하

**감지**
- HikariCP 커넥션 타임아웃 → WARN 로그(`connectionTimeout` 초과)
- Micrometer `event.collect.duration` P99 급등
- `/actuator/health`의 `db` 컴포넌트 상태 DOWN

**완화 — 커넥션 고갈**
- HikariCP 풀 크기를 `application-prod.yml`에서 환경별로 분리
- 읽기 요청을 Read Replica로 라우팅해 Primary 부하 분산
- 장기 트랜잭션에 타임아웃 설정으로 커넥션 조기 반환

**완화 — 락 경합**
- `chat_events` INSERT IGNORE는 UNIQUE 인덱스 락만 발생, 기존 행 갱신 없음
- Snapshot 생성은 비동기(`@Async`)로 이벤트 수집 트랜잭션과 분리
- 트랜잭션 범위를 Application Service 메서드 단위로 제한해 락 보유 시간 최소화

**복구**
- Primary 복구 또는 Replica 승격 후 커넥션 풀 자동 재연결
- 중단 기간 클라이언트 재전송 + Idempotency로 중복 없이 재수집

---

### 3. 데이터 유실 또는 정합성 이슈

**감지**
- 중복 이벤트: `event.duplicate.blocked` 카운터 급증
- 부분 실패: 에러 로그에서 트랜잭션 롤백 반복 감지
- 정합성 이슈: Full Replay 결과와 Snapshot+Delta 결과 불일치

**완화 — 중복 저장**
- Redis 1차: `clientEventId` 존재 시 DB 접근 없이 즉시 반환
- MySQL 2차: `INSERT IGNORE` + `UNIQUE(session_id, client_event_id)` → silent skip
- 두 방어 모두 실패해도 기존 이벤트를 조회해 200 반환, DB 오염 없음

**완화 — 부분 실패**
- Snapshot 생성은 `@TransactionalEventListener(AFTER_COMMIT)` → 이벤트 수집 트랜잭션과 독립
- Snapshot 실패 → WARN 로그 + Full Replay 폴백, 비즈니스 중단 없음

**복구**
- `chat_events` Append-only 특성상 이미 저장된 데이터는 변경되지 않음
- Snapshot 손상/누락 시 Full Replay로 언제든 정확한 상태 복원 가능
- 수동으로 `SnapshotApplicationService.createSnapshot(sessionId)` 호출해 Snapshot 재생성
