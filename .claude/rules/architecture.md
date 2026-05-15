# 아키텍처 규칙

## 패키지 구조

```
com.creatived.chat
├── domain
│   ├── session       # Session, SessionStatus, Participant, SessionRepository
│   ├── event         # ChatEvent, EventType, ClientEventId, ChatEventId, ChatEventRepository
│   └── snapshot      # Snapshot, SessionState, EventProjector, SnapshotRepository
├── application
│   ├── session       # SessionApplicationService, 각 Command/Query
│   ├── event         # EventApplicationService, CollectEventCommand
│   ├── snapshot      # SnapshotApplicationService, SnapshotEventListener, EventCollectedEvent
│   └── support       # @UseCase, @Idempotent, AOP Aspects
├── infrastructure
│   ├── persistence
│   │   ├── session   # JpaEntity, JpaRepository, RepositoryAdapter
│   │   ├── event
│   │   ├── snapshot
│   │   └── converter # UuidBinaryConverter, JsonMapConverter
│   ├── redis         # IdempotencyKeyStore, PresenceStore
│   ├── datasource    # DataSourceRoutingAspect, DataSourceContextHolder
│   └── config        # SwaggerConfig, AsyncConfig, Properties
└── presentation
    ├── rest          # Controllers, GlobalExceptionHandler
    │   └── dto
    └── websocket     # ChatWebSocketHandler
```

## 레이어 의존 방향

```
Presentation → Application → Domain ← Infrastructure
```

- `domain`: 외부 패키지 의존 없음. 순수 Java만 허용.
- `application`: `domain`에만 의존. `infrastructure` 직접 참조 금지.
- `infrastructure`: `domain` Repository 인터페이스를 구현. `application` 참조 금지.
- `presentation`: `application`에만 의존.

## Aggregate 경계

| Aggregate | 소유 관계 | 불변 규칙 |
|-----------|----------|----------|
| `Session` | `Participant`를 소유. 세션을 통해서만 추가/제거. | ACTIVE 상태에서만 참여·메시지 허용 |
| `ChatEvent` | 세션과 독립. Append-only. | 저장 후 수정·삭제 불가 |
| `Snapshot` | 성능 최적화 목적. 비즈니스 규칙 없음. | 없어도 Full Replay로 복원 가능해야 함 |
| `SessionState` | Projection 출력값. Value Object. | 불변(immutable). `with...()` 메서드로 새 인스턴스 반환 |

## Repository 인터페이스

- 인터페이스: `domain` 패키지 위치.
- 구현체(`...RepositoryAdapter`): `infrastructure/persistence` 위치.
- 메서드명은 도메인 언어로 작성. JPA 메서드 명명 규칙이 비즈니스 의미를 해치면 래핑한다.

## 트랜잭션 경계

- `@Transactional`은 Application Service 메서드에만 선언.
- 읽기 전용 조회는 `@Transactional(readOnly = true)` 명시 → Read Replica 자동 라우팅.

## DTO / Command 범위

- Request/Response DTO: `presentation` 레이어에만 존재.
- Application Service 입력: Command/Query 객체 사용 (`CollectEventCommand`, `GetSessionListQuery` 등).
- 도메인 객체를 API 응답으로 직접 노출하지 않는다.

## 횡단 관심사 — AOP

Service 메서드에 직접 작성하지 않는다.

| 관심사 | 구현 |
|--------|------|
| 로그 + MDC | `@UseCase` → `UseCaseLoggingAspect` |
| 중복 이벤트 차단 | `@Idempotent` → `IdempotencyAspect` |
| Read/Write 분기 | `DataSourceRoutingAspect` |

## 설계 원칙

- **KISS**: 지금 당장 필요한 것만 만든다. 미래 요구사항을 위한 선제 추상화 금지.
- **Fail Fast**: 비즈니스 규칙 위반은 해당 도메인 메서드에서 즉시 예외. null 반환 금지.
- **OCP (이벤트 타입 확장)**: 새 이벤트 타입은 `EventProjector` 구현 클래스 추가로 해결. 기존 코드 수정 없음.

## 예외 계층

```
DomainException (RuntimeException)
  ├── SessionNotFoundException       → 404
  ├── AlreadyJoinedException         → 409
  ├── InvalidSessionStateException   → 422
  └── UnsupportedEventTypeException  → 400
```

`GlobalExceptionHandler`가 HTTP 응답으로 변환. Application Service는 도메인 예외를 잡지 않는다.
