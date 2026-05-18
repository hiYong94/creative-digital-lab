# 이벤트 기반 상태 복원

이벤트 타입 정의, Projection 전략, Snapshot 최적화, 구현 결과를 기술합니다.

---

## 이벤트 타입

| 타입 | 트리거 | Projection 효과 |
|------|--------|----------------|
| `JOIN` | 세션 참여 (REST + WebSocket) | `participants`에 userId 추가 |
| `LEAVE` | 정상 퇴장 | `participants`에서 userId 제거 |
| `MESSAGE` | 메시지 전송 | `messages`에 내용 추가 |
| `DISCONNECT` | 비정상 연결 끊김 | 참여자 오프라인 마킹 |
| `RECONNECT` | 재연결 | 참여자 온라인 복원 |

REST로 세션 참여(`POST /sessions/{id}/participants`)할 때도 `JOIN` 이벤트를 `chat_events`에 자동 저장합니다. 이로써 timeline 복원 시 참여자 정보가 정확히 반영됩니다.

---

## Projection 아키텍처

각 이벤트 타입마다 독립적인 `EventProjector` 구현체가 있습니다.

```
EventProjectorRegistry
    ├─ JOIN       → JoinProjector
    ├─ LEAVE      → LeaveProjector
    ├─ MESSAGE    → MessageProjector
    ├─ DISCONNECT → DisconnectProjector
    └─ RECONNECT  → ReconnectProjector
```

새 이벤트 타입 추가 시 `EventProjector` 구현 클래스를 추가하는 것으로 완료됩니다. 기존 Projector 수정이 필요 없습니다(OCP).

`SessionState`는 불변(immutable) Value Object입니다. 각 Projector의 `apply()`는 새 `SessionState` 인스턴스를 반환합니다.

---

## 복원 전략

### Full Replay

```
chat_events (at 이전 전체)
    │ ORDER BY server_received_at ASC, sequence_no ASC
    ▼
applyEvents(SessionState.empty(), events)
    ▼
SessionState 반환 (restoredFrom: "FULL_REPLAY")
```

Snapshot이 없거나, 존재하는 Snapshot이 복원 시점(`at`) 이후일 때 사용합니다.
이벤트 N개면 N번의 Projection 연산이 필요하므로 이벤트가 쌓일수록 비용이 증가합니다.

### Snapshot + Delta Replay

```
최신 Snapshot (last_sequence_no = S)
    │ snapshot.state (JSON → SessionState 역직렬화)
    ▼
chat_events (sequence_no > S AND server_received_at <= at)
    │ Delta 이벤트만 (최대 49건)
    ▼
applyEvents(snapshot.state, deltaEvents)
    ▼
SessionState 반환 (restoredFrom: "SNAPSHOT_PLUS_REPLAY")
```

이벤트가 아무리 쌓여도 복원 비용이 O(interval-1) = O(49)로 고정됩니다.

### 전략 선택 로직

```java
// SnapshotApplicationService.restoreAt()
Optional<Snapshot> snapshotOpt = snapshotRepository.findLatestBySessionId(sessionId);

if (snapshotOpt.isPresent()) {
    Snapshot snapshot = snapshotOpt.get();
    long maxApplicableSeqNo = applicableEvents.stream()
            .mapToLong(ChatEvent::getSequenceNo).max().orElse(-1L);

    if (snapshot.getLastSequenceNo() <= maxApplicableSeqNo) {
        // Snapshot이 at 이전에 존재 → Delta Replay
        List<ChatEvent> deltaEvents = applicableEvents.stream()
                .filter(e -> e.getSequenceNo() > snapshot.getLastSequenceNo())
                .toList();
        return new TimelineResult(applyEvents(snapshot.getState(), deltaEvents), "SNAPSHOT_PLUS_REPLAY");
    }
}
// Snapshot 없음 또는 at 이후 → Full Replay
return new TimelineResult(applyEvents(SessionState.empty(), applicableEvents), "FULL_REPLAY");
```

---

## 중복 이벤트 처리 (복원 로직)

`applyEvents()` 내부에서 `appliedSequenceNos` Set을 유지합니다.

```java
private SessionState applyEvents(SessionState initial, List<ChatEvent> events) {
    SessionState state = initial;
    Set<Long> appliedSequenceNos = new HashSet<>();

    for (ChatEvent event : events) {
        if (appliedSequenceNos.contains(event.getSequenceNo())) {
            log.warn("중복 sequenceNo 건너뜀: sequenceNo={}", event.getSequenceNo());
            continue;
        }
        state = projectorRegistry.get(event.getType()).apply(state, event);
        appliedSequenceNos.add(event.getSequenceNo());
    }
    return state;
}
```

DB 저장 레벨에서 `UNIQUE(session_id, client_event_id)`로 중복 저장은 이미 차단됩니다. `appliedSequenceNos` 검사는 Snapshot+Delta 경계에서 delta 이벤트가 Snapshot에 이미 포함된 경우를 방어합니다.

### SessionState 불변성 보장

`withParticipantAdded()`는 내부적으로 `LinkedHashSet`을 사용합니다. 동일 userId가 중복 적용되어도 Set이 자동으로 무시하므로 참여자 중복이 발생하지 않습니다.

---

## 순서 뒤바뀜 처리

```sql
ORDER BY server_received_at ASC, sequence_no ASC
```

Projection은 이미 정렬된 이벤트를 순서대로 소비합니다. 순서 뒤바뀜은 복원 로직에 도달하기 전에 DB 쿼리 레벨에서 해소됩니다.

클라이언트 시계(`client_sent_at`)를 정렬에 사용하지 않는 이유: 기기마다 시계 편차가 있어 멀티 기기 환경에서 일관된 순서를 보장할 수 없습니다.

---

## Snapshot 자동 생성 (가산점)

### 생성 조건

```
이벤트 수집 성공 (트랜잭션 커밋 후)
    └─ count % 50 == 0 → Snapshot 생성
```

`snapshotProperties.interval()` 값으로 주기를 외부화했습니다(기본 50).

### 비동기 파이프라인

```
EventApplicationService.collect() → 커밋
    └─ EventCollectedEvent 발행
        └─ @TransactionalEventListener(AFTER_COMMIT)
            └─ @Async("snapshotTaskExecutor")
                └─ SnapshotEventListener.onEventCollected()
```

- `AFTER_COMMIT`: 미완료 트랜잭션 상태가 Snapshot에 포함되지 않음
- `@Async`: Snapshot 생성 지연이 이벤트 수집 응답 시간에 영향을 주지 않음
- 실패 시 WARN 로그만 기록. Snapshot 없어도 Full Replay로 항상 복원 가능

---

## 재연결 Resume / Replay (가산점)

재연결 시 클라이언트가 `resumeFromSequenceNo`를 전달하면 서버는 해당 sequenceNo 이후 이벤트만 재전송합니다. 전체 이벤트를 다시 받을 필요가 없습니다.

```
클라이언트: { resumeFromSequenceNo: 42 }
서버: SELECT ... WHERE session_id = ? AND sequence_no > 42
      ORDER BY server_received_at ASC, sequence_no ASC
→ /user/queue/missed 전송
```

연결 중단 기간이 길어 누락 이벤트가 1,000개를 초과하면 마지막 100개만 전송하고 전체 상태 동기화를 유도합니다.

```
missed.size() > 1000
    └─ missed = missed.subList(missed.size() - 100, missed.size())
       + WARN 로그
       클라이언트: GET /sessions/{id}/timeline?at=now 로 전체 동기화
```

---

## 복원 결과 예시

```json
GET /sessions/{id}/timeline?at=2026-01-01T12:00:00

{
  "sessionId": "...",
  "restoredFrom": "SNAPSHOT_PLUS_REPLAY",
  "at": "2026-01-01T12:00:00",
  "participants": ["user1", "user2"],
  "messages": [
    { "userId": "user1", "content": "안녕하세요", "sequenceNo": 3 },
    { "userId": "user2", "content": "반갑습니다", "sequenceNo": 5 }
  ]
}
```

`restoredFrom` 필드로 어떤 전략이 적용되었는지 확인할 수 있습니다.
