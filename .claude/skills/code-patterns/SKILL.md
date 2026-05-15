---
name: code-patterns
description: |
  이 프로젝트의 코드 패턴 템플릿 모음이다.

  반드시 다음 상황에서 본 skill 을 호출하라:
  - 새 도메인 클래스, Application Service, Repository, AOP, Controller, DTO를 생성할 때
  - 기존 코드와 일관성을 맞춰야 할 때
  - "/code-patterns" 또는 "패턴 참조" 요청 시

  RULES.md의 규칙을 위반하지 않는 선에서 아래 패턴을 적용한다.
---

# code-patterns

아래 Skill 번호는 구현 순서가 아니라 참조 번호다.
코드 생성 전 해당 Skill을 확인하고 패턴을 일관되게 적용한다.

---

## Skill 1: Value Object — 식별자 타입

- `record`로 선언. `UUID value` 필드 단일 보유.
- 팩토리 메서드 3종: `newId()` (서버 채번), `from(UUID)`, `from(String)`.
- `toString()` → `value.toString()` 오버라이드.
- 적용 대상: `SessionId`, `ChatEventId`, `ParticipantId`.

`ClientEventId`는 클라이언트가 만든 문자열 Idempotency Key:
- `of(String)` 에서 null·blank 즉시 예외. 빈 값으로 생성할 수 없다.
- 필드 타입: `String value` (UUID 아님).

---

## Skill 2: Aggregate Root — `Session`

- 생성: `Session.create(initiatorId)` — 서버가 `SessionId` 발급, 생성자가 첫 참여자 자동 추가.
- 복원: `Session.reconstitute(...)` — 저장소 조회 시 사용. 비즈니스 규칙을 검증하지 않는다.
- 상태 변경 메서드: `addParticipant`, `end` — ACTIVE 상태 아니면 도메인 예외.
- `participants`는 `Collections.unmodifiableList()` 반환. 외부 변이 불가.
- `SessionStatus`: `ACTIVE`, `ENDED`, `SUSPENDED`.

`Participant`: `of(sessionId, userId)` 로 생성. `reconstitute(...)` 로 복원.

---

## Skill 3: Domain Entity — `ChatEvent`

- 생성: `ChatEvent.create(...)` — 서버가 `ChatEventId` 발급, `serverReceivedAt = Instant.now()`, `newlyCreated = true`.
- 복원: `ChatEvent.reconstitute(...)` — `newlyCreated = false`.
- `newlyCreated` 플래그: HTTP 응답 코드 결정(201 vs 200)에 사용.
- `payload`: `Collections.unmodifiableMap()` 반환.
- `EventType`: `MESSAGE`, `JOIN`, `LEAVE`, `DISCONNECT`, `RECONNECT`.

---

## Skill 4: Value Object — `SessionState` (Projection 출력)

- `record`로 선언. 불변.
- `with...()` 메서드는 항상 새 인스턴스 반환. 필드를 직접 변경하지 않는다.
- 초기값: `SessionState.empty(sessionId, asOf)` — 참여자·메시지 없는 빈 상태.
- 주요 메서드: `addParticipant`, `removeParticipant`, `addMessage`, `withRestoredFrom`.
- `restoredFrom`: `"FULL_REPLAY"` 또는 `"SNAPSHOT_PLUS_REPLAY"`.

`MessageView`: `from(ChatEvent)` 팩토리 메서드로 생성. `payload.get("text")` 추출.

---

## Skill 5: AOP — `@UseCase` (MDC + 로그)

- `@UseCase("유즈케이스명")` → `UseCaseLoggingAspect`가 자동 처리.
- 메서드 진입 시: `traceId`(UUID 생성), `useCase`, `sessionId`, `userId` → MDC 주입.
- 완료/실패 INFO·ERROR 로그 자동 출력. 소요시간 포함.
- `finally` 블록에서 반드시 `MDC.clear()`.
- Application Service 메서드에만 선언. 서비스 내부에 로그 코드 직접 작성 금지.

---

## Skill 6: AOP — `@Idempotent` (중복 이벤트 차단)

- `@Idempotent` → `IdempotencyAspect`가 자동 처리.
- 실행 순서: ① Redis에서 `clientEventId` 존재 확인 → 있으면 기존 이벤트 반환 → ② proceed() → ③ Redis에 결과 저장(TTL 10분).
- `CollectEventCommand`를 파라미터로 갖는 메서드에만 적용.
- `@UseCase`와 함께 선언할 때 순서: `@UseCase` → `@Idempotent` → `@Transactional`.

---

## Skill 7: Redis — `IdempotencyKeyStore`

- 키 형식: `idempotency:{sessionId}:{clientEventId}`
- sessionId 포함 이유: 다른 세션에서 같은 clientEventId 충돌 방지.
- TTL: 10분. 만료 후 MySQL UNIQUE 제약이 2차 방어.
- 조회: `Optional<ChatEventId>` 반환.

---

## Skill 8: Redis — `PresenceStore`

- 키 형식: `session:{sessionId}:presence` (Redis Hash).
- 필드: `userId` → `"ONLINE"` | `"OFFLINE"`.
- TTL: 300초. heartbeat 수신마다 `expire()` 갱신.
- `setOffline()`: 필드를 `"OFFLINE"`으로 표시. 키를 삭제하지 않는다 (참여 이력 유지).
- TTL 만료 = 오프라인 간주.

---

## Skill 9: Strategy — `EventProjector` (OCP)

- 인터페이스: `supportedType()`, `apply(SessionState, ChatEvent)`.
- 구현체: `MessageProjector`, `JoinProjector`, `LeaveProjector`, `DisconnectProjector`, `ReconnectProjector`.
- `DISCONNECT`·`RECONNECT` Projector는 `state`를 그대로 반환 (참여자 목록 변경 없음).
- `ProjectionBuilderFactory`: `List<EventProjector>` 주입 → `Map<EventType, EventProjector>` 구성. 새 Projector 추가 시 기존 코드 무수정.

---

## Skill 10: Command / Query 객체

- Application Service 입력은 모두 Command·Query `record`로 정의.
- 위치: `application/{domain}/` 패키지.
- 주요 Command: `CreateSessionCommand`, `JoinSessionCommand`, `EndSessionCommand`, `CollectEventCommand`, `GetSessionListQuery`.
- `CollectEventCommand`는 정적 팩토리 메서드 추가: `ofDisconnect(sessionId, userId)`, `ofReconnect(sessionId, userId, clientEventId)` — 서버 내부 생성 시 `clientEventId`를 서버가 UUID로 발급.

---

## Skill 11: Application Service — `SessionApplicationService`

- 메서드마다 `@UseCase("설명")` + `@Transactional` 또는 `@Transactional(readOnly = true)` 선언.
- `findById` 결과 없으면 `SessionNotFoundException` 즉시 throw. null 반환 금지.
- 도메인 메서드 호출 후 `sessionRepository.save()` 명시적 호출.

---

## Skill 12: Application Service — `EventApplicationService`

- `collect()`: `@UseCase` + `@Idempotent` + `@Transactional` 3중 선언.
- 저장 후 `ApplicationEventPublisher.publishEvent(EventCollectedEvent(sessionId, totalCount))` 발행.
- `findMissed(sessionId, resumeFromSequenceNo)`: 1000개 초과 시 마지막 100개만 반환 + WARN 로그.

---

## Skill 13: `SnapshotApplicationService` — Timeline 복원

복원 흐름:
1. `snapshotRepository.findLatestBeforeTime(sessionId, at)` 조회.
2. Snapshot 있으면 → `snapshot.getState()` 베이스, `snapshot.createdAt` 이후 이벤트만 재생 → `strategy = "SNAPSHOT_PLUS_REPLAY"`.
3. Snapshot 없으면 → `SessionState.empty()` 베이스, 전체 이벤트 재생 → `strategy = "FULL_REPLAY"`.
4. 재생 중 `ClientEventId` 중복 감지 시 WARN 로그 후 건너뜀.

---

## Skill 14: `@TransactionalEventListener` — Snapshot 비동기 생성

- `SnapshotEventListener.onEventCollected(EventCollectedEvent)` 에 두 애노테이션 병행 선언:
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` — 트랜잭션 커밋 완료 후 실행.
  - `@Async("snapshotTaskExecutor")` — 별도 스레드에서 실행.
- 50개(`snapshotProps.interval()`) 단위마다 생성.
- 실패 시 `WARN` 로그만. 예외를 전파하지 않는다.

---

## Skill 15: `AsyncConfig` — 스레드 풀

- `@Configuration` + `@EnableAsync`.
- `ThreadPoolTaskExecutor` 사용. `SimpleAsyncTaskExecutor` 절대 금지.
- Bean 이름: `"snapshotTaskExecutor"`.
- `RejectedExecutionHandler`: `CallerRunsPolicy` (큐 포화 시 호출 스레드에서 실행).
- 풀 크기, 큐 용량은 Properties로 외부화.

---

## Skill 16: WebSocket — STOMP 설정 + `ChatWebSocketHandler`

설정 (`WebSocketConfig`):
- `registerStompEndpoints`: `/ws` 엔드포인트.
- `enableStompBrokerRelay("/topic")`: Redis Broker Relay 연결.
- `setApplicationDestinationPrefixes("/app")`.
- `setMessageSizeLimit(64 * 1024)`.

핸들러 (`ChatWebSocketHandler`):
- `@MessageMapping` 3종: `/session/{id}/message`, `/session/{id}/reconnect`, `/session/{id}/heartbeat`.
- `@EventListener(SessionDisconnectEvent)`: 연결 끊김 감지 → DISCONNECT 이벤트 자동 수집.
- Heartbeat: `presenceStore.refreshTtl()` 만 호출. DB 기록 없음.
- 재연결: presenceStore 업데이트 → RECONNECT 이벤트 수집 → 누락 이벤트 `/user/queue/missed` 전송.

---

## Skill 17: SpringDoc OpenAPI 3

- `SwaggerConfig`: `OpenAPI` Bean 선언. 제목·설명·버전 설정.
- Request DTO 필드: `@Schema(description, example, requiredMode)`.
- Controller 메서드: `@Operation(summary)`.
- 공통 에러 응답: `Components.addResponses()`로 등록.

---

## Skill 18: REST Controller

- `@RestController` + `@RequestMapping("/sessions")`.
- 입력 DTO → Command 변환 → Application Service 호출 → Response DTO 변환.
- `SessionId.from(pathVariable)` 으로 변환. Controller에서 UUID 직접 사용 금지.
- 생성 응답: `ResponseEntity.status(HttpStatus.CREATED).body(...)`.

---

## Skill 19: 에러 응답 — `ErrorResponse` + `GlobalExceptionHandler`

`ErrorResponse` 필드: `code`, `message`, `traceId` (MDC에서 주입).

`GlobalExceptionHandler` 처리 목록:
| 예외 | HTTP 코드 | code |
|------|-----------|------|
| `SessionNotFoundException` | 404 | `SESSION_NOT_FOUND` |
| `AlreadyJoinedException` | 409 | `ALREADY_JOINED` |
| `InvalidSessionStateException` | 422 | `INVALID_SESSION_STATE` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` |

---

## Skill 20: `DataSourceRoutingAspect`

- `@Order(Ordered.HIGHEST_PRECEDENCE)` — `@Transactional` AOP보다 먼저 실행.
- `readOnly = true` → `DataSourceType.READER`, 그 외 → `DataSourceType.WRITER`.
- `DataSourceContextHolder` (ThreadLocal)로 현재 라우팅 대상 관리.
- `finally` 블록에서 반드시 `DataSourceContextHolder.clear()`.

---

## Skill 21: `@ConfigurationProperties` — 설정 외부화

`@ConfigurationProperties(prefix = "chat.snapshot")` → `SnapshotProperties(int interval, boolean asyncEnabled)`.
`@ConfigurationProperties(prefix = "chat.redis")` → `RedisProperties(String host, int port, int idempotencyTtlMinutes, int presenceTtlSeconds)`.

기본값: `snapshot.interval = 50`, `redis.idempotencyTtlMinutes = 10`, `redis.presenceTtlSeconds = 300`.

---

## Skill 22: MySQL DDL

```sql
CREATE TABLE sessions (
    id         BINARY(16)   NOT NULL PRIMARY KEY,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6)  NOT NULL DEFAULT NOW(6),
    ended_at   DATETIME(6)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE participants (
    id         BINARY(16)   NOT NULL PRIMARY KEY,
    session_id BINARY(16)   NOT NULL,
    user_id    VARCHAR(64)  NOT NULL,
    joined_at  DATETIME(6)  NOT NULL DEFAULT NOW(6),
    left_at    DATETIME(6)  NULL,
    CONSTRAINT uq_participant         UNIQUE (session_id, user_id),
    CONSTRAINT fk_participant_session FOREIGN KEY (session_id) REFERENCES sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_events (
    id                 BINARY(16)   NOT NULL PRIMARY KEY,
    session_id         BINARY(16)   NOT NULL,
    user_id            VARCHAR(64)  NOT NULL,
    client_event_id    VARCHAR(64)  NOT NULL,
    type               VARCHAR(20)  NOT NULL,
    payload            JSON         NOT NULL,
    sequence_no        BIGINT       NOT NULL,
    server_received_at DATETIME(6)  NOT NULL DEFAULT NOW(6),
    CONSTRAINT uq_event_idempotency UNIQUE (session_id, client_event_id),
    CONSTRAINT fk_event_session     FOREIGN KEY (session_id) REFERENCES sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE snapshots (
    id               BINARY(16)   NOT NULL PRIMARY KEY,
    session_id       BINARY(16)   NOT NULL,
    last_event_id    BINARY(16)   NULL,
    state            JSON         NOT NULL,
    last_sequence_no BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT NOW(6),
    CONSTRAINT fk_snapshot_session FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_snapshot_event   FOREIGN KEY (last_event_id) REFERENCES chat_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Skill 23: MySQL 인덱스

```sql
CREATE INDEX idx_events_session_time     ON chat_events (session_id, server_received_at);
CREATE INDEX idx_events_session_sequence ON chat_events (session_id, sequence_no);
CREATE INDEX idx_snapshots_session_time  ON snapshots   (session_id, created_at DESC);
CREATE INDEX idx_sessions_status_created ON sessions    (status, created_at DESC);
```

---

## Skill 24: MySQL 핵심 쿼리

```sql
-- Snapshot + Delta: 직전 스냅샷 조회
SELECT * FROM snapshots
WHERE session_id = :sessionId AND created_at <= :at
ORDER BY created_at DESC LIMIT 1;

-- Delta 재생 이벤트 (Snapshot 이후 ~ at 사이)
SELECT * FROM chat_events
WHERE session_id = :sessionId
  AND server_received_at > :snapshotCreatedAt
  AND server_received_at <= :at
ORDER BY server_received_at ASC, sequence_no ASC;

-- Idempotency INSERT (중복 시 무시)
INSERT IGNORE INTO chat_events
    (id, session_id, user_id, client_event_id, type, payload, sequence_no, server_received_at)
VALUES (:id, :sessionId, :userId, :clientEventId, :type, :payload, :sequenceNo, NOW(6));

-- 재연결 누락 이벤트
SELECT * FROM chat_events
WHERE session_id = :sessionId AND sequence_no > :resumeFromSequenceNo
ORDER BY server_received_at ASC, sequence_no ASC;
```

---

## Skill 25: `UuidBinaryConverter` — UUID ↔ BINARY(16)

```java
@Converter(autoApply = true)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {
    @Override
    public byte[] convertToDatabaseColumn(UUID uuid) {
        if (uuid == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
    @Override
    public UUID convertToEntityAttribute(byte[] bytes) {
        if (bytes == null) return null;
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
```

---

## Skill 26: `JsonMapConverter` — JSON 컬럼 처리

- `AttributeConverter<Map<String, Object>, String>` 구현.
- `convertToDatabaseColumn`: null이면 `"{}"` 반환.
- `convertToEntityAttribute`: null·blank면 `Map.of()` 반환.
- `ObjectMapper`는 static 싱글턴으로 선언.
- `@Converter(autoApply = false)`: 적용 대상 필드에 `@Convert(converter = JsonMapConverter.class)` 명시.

---

## Skill 27: JPA Entity — Domain Entity와 분리

- 위치: `infrastructure/persistence/{domain}/` 패키지.
- `@Entity`, `@Table`, `@NoArgsConstructor(access = PROTECTED)` 선언.
- `from(DomainObject)` — 도메인 → JPA Entity 변환.
- `toDomain()` — JPA Entity → 도메인 객체 복원 (`reconstitute()` 호출).
- 도메인 패키지에 JPA 애노테이션 사용 절대 금지.

---

## Skill 28: Micrometer — 커스텀 메트릭

- `ChatMetrics` Bean: `MeterRegistry` 주입.
- `@PostConstruct`에서 Gauge 등록 (활성 세션 수, WebSocket 연결 수).
- Counter: `chat.events.collected.total` (태그: `type`), `chat.events.duplicate.total`.
- Timer: `chat.timeline.restore.duration` (태그: `strategy`).
- Application Service에서 직접 호출하지 않고 AOP에서 기록한다.

---

## Skill 29: Fake Repository — 단위 테스트용 인메모리 구현체

- `SessionRepository`, `ChatEventRepository`, `SnapshotRepository` 각각에 Fake 작성.
- Fake는 `test` 소스셋에만 위치. `HashMap`·`ArrayList`로 저장·조회 구현.
- 도메인 규칙 검증 없음. 저장·조회 동작만 시뮬레이션.
- Application Service 테스트에서 Fake를 생성자로 직접 주입해 사용.
- `FakeIdempotencyKeyStore`: `Set<String>` 으로 처리된 키 관리. TTL 무시.
