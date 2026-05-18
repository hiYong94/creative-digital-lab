# Creative Digital Lab — 백엔드 사전 과제

1:1 실시간 채팅 + Event Sourcing 기반 상태 복원 서비스

---

## 제출물 문서

| 문서 | 내용 |
|------|------|
| [docs/db-design.md](docs/db-design.md) | ERD, 핵심 DDL, 인덱스 설계 근거 |
| [docs/query-optimization.md](docs/query-optimization.md) | 핫패스 쿼리 3개, 인덱스 근거, 병목 분석 |
| [docs/design.md](docs/design.md) | 재연결, 중복 처리, 확장성, 관측 가능성, 장애 대응 |
| [docs/event-sourcing.md](docs/event-sourcing.md) | 이벤트 복원 전략, Snapshot+Delta, Projection |

---

## 실행 방법

### 사전 요구사항

- Docker & Docker Compose
- Java 17
- Gradle (Wrapper 포함)

### 1. 인프라 기동

```bash
docker compose up -d
```

MySQL 8.0 (3306), Redis 7 (6379)가 기동됩니다.

### 2. 애플리케이션 기동

```bash
./gradlew bootRun
```

기동 시 `db/schema.sql`이 자동 실행되어 테이블이 생성됩니다.

### 3. 확인

| URL | 설명 |
|-----|------|
| `http://localhost:8080/swagger-ui.html` | OpenAPI 문서 |
| `http://localhost:8080/actuator/health` | 헬스체크 |

---

## 환경 구성

| 기술 | 역할 |
|------|------|
| Spring Boot 4.0.6 / Java 17 | 애플리케이션 |
| MySQL 8.0 | 이벤트 로그, 세션, Snapshot 영속 |
| Redis 7 | Idempotency 키 캐시 (TTL 10분), Presence (TTL 300초) |
| WebSocket STOMP | 실시간 메시지 송수신 |
| SpringDoc OpenAPI 3 | Swagger UI 자동 생성 |

### 프로파일

```bash
# 로컬 (기본)
./gradlew bootRun

# 환경 변수 오버라이드 예시
DB_URL=jdbc:mysql://host:3306/creative-digital-lab \
DB_PASSWORD=secret \
REDIS_HOST=host \
./gradlew bootRun
```

---

## API 명세

OpenAPI 전체 명세는 기동 후 `/swagger-ui.html`에서 확인하세요.

### Session

| 메서드 | 경로 | 설명 | 응답 |
|--------|------|------|------|
| `POST` | `/sessions` | 세션 생성 | 201 |
| `GET` | `/sessions?status=&from=&to=&page=&size=` | 세션 목록 | 200 |
| `GET` | `/sessions/{id}` | 세션 단건 조회 | 200 |
| `POST` | `/sessions/{id}/participants` | 세션 참여 | 201 |
| `DELETE` | `/sessions/{id}` | 세션 종료 | 200 |
| `GET` | `/sessions/{id}/participants/{userId}/online` | 온라인 상태 조회 | 200 |

> `/sessions/{id}/participants` 는 요구사항 예시의 `/sessions/{id}/join`과 동일한 역할입니다. RESTful 자원 기반 URL 원칙을 적용해 변경했습니다. `DELETE /sessions/{id}` 는 `/sessions/{id}/end`와 동일합니다.

### Event

| 메서드 | 경로 | 설명 | 응답 |
|--------|------|------|------|
| `POST` | `/sessions/{id}/events` | 이벤트 수집 (멱등) | 200 |
| `GET` | `/sessions/{id}/events?from=&to=` | 이벤트 기간 조회 | 200 |

### Timeline

| 메서드 | 경로 | 설명 | 응답 |
|--------|------|------|------|
| `GET` | `/sessions/{id}/timeline?at=` | 특정 시점 상태 복원 | 200 |

`at` 파라미터: ISO 8601 형식 (`2026-01-01T12:00:00`)

### WebSocket (STOMP)

연결: `ws://localhost:8080/ws`

| 방향 | 경로 | 설명 |
|------|------|------|
| 클라이언트 → 서버 | `/app/sessions/{id}/events` | 이벤트 전송 |
| 클라이언트 → 서버 | `/app/sessions/{id}/reconnect` | 재연결 + 누락 이벤트 요청 |
| 클라이언트 → 서버 | `/app/sessions/{id}/heartbeat` | Presence 갱신 |
| 서버 → 전체 | `/topic/sessions/{id}` | 이벤트 브로드캐스트 |
| 서버 → 개인 | `/user/queue/missed` | 누락 이벤트 재전송 |

---

## 아키텍처

```
com.creatived.chat
├── domain          # 순수 Java. 외부 의존 없음
│   ├── session     # Session, Participant, SessionRepository (인터페이스)
│   ├── event       # ChatEvent, EventType, ChatEventRepository (인터페이스)
│   └── snapshot    # SessionState, Snapshot, EventProjector, SnapshotRepository (인터페이스)
├── application     # 유즈케이스 조합. domain에만 의존
│   ├── session     # SessionApplicationService
│   ├── event       # EventApplicationService
│   ├── snapshot    # SnapshotApplicationService, SnapshotEventListener
│   └── support     # @UseCase, @Idempotent, AOP Aspects
├── infrastructure  # domain 인터페이스 구현. Spring, JPA, Redis 의존
│   ├── persistence # JpaEntity, JpaRepository, RepositoryAdapter
│   ├── redis       # RedisIdempotencyKeyStore, PresenceStore
│   └── config      # WebSocketConfig, AsyncConfig, SwaggerConfig
└── presentation    # HTTP / WebSocket 진입점
    ├── rest        # Controllers, GlobalExceptionHandler
    └── websocket   # ChatWebSocketHandler
```

레이어 의존 방향: `Presentation → Application → Domain ← Infrastructure`

Domain은 어떤 외부 기술에도 의존하지 않습니다.

---

## 주요 의사결정

### 1. Event Sourcing — Append-only 이벤트 로그

`chat_events`는 UPDATE/DELETE 금지. 상태 변경은 새 이벤트 추가로만 표현합니다. 이벤트 로그의 불변성이 타임라인 복원의 정확성을 보장합니다.

### 2. 이벤트 정렬 기준 — 서버 수신 시각 우선

정렬 1순위: `server_received_at ASC` / 2순위: `sequence_no ASC`. 클라이언트 시계는 기기마다 편차가 있으므로 정렬 기준으로 사용하지 않습니다.

### 3. Idempotency — Redis + MySQL 이중 방어

Redis 1차(TTL 10분) → MySQL `INSERT IGNORE` + `UNIQUE(session_id, client_event_id)` 2차. Redis 장애 시 MySQL이 최후 방어선입니다. 자세한 내용은 [design.md](docs/design.md)를 참조하세요.

### 4. Full Replay vs Snapshot + Delta Replay

Snapshot 없으면 Full Replay, 있으면 Snapshot+Delta(최대 49건)로 자동 전환합니다. 자세한 내용은 [event-sourcing.md](docs/event-sourcing.md)를 참조하세요.

### 5. Snapshot 자동 생성 — 비동기 + AFTER_COMMIT

50 이벤트마다 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 비동기 생성합니다. Snapshot 실패는 비즈니스를 중단시키지 않습니다.

### 6. 1:1 정원 제한 — 도메인 레이어에서 강제

`Session.join()`이 직접 검증합니다. 어느 경로로 참여해도 정원 초과를 막을 수 있습니다.

### 7. UUID → BINARY(16) 저장

36자 문자열 대신 16바이트로 저장해 PK 인덱스 크기와 조회 성능을 개선합니다. 자세한 내용은 [db-design.md](docs/db-design.md)를 참조하세요.

---

## 비목표 / 알려진 한계

| 항목 | 설명 |
|------|------|
| 인증/인가 | `userId`는 클라이언트 제공 문자열. 토큰 기반 인증 없음 |
| 메시지 암호화 | 전송/저장 암호화 미적용 |
| 파일/이미지 전송 | 단일 메시지 64KB 제한. 바이너리 전송 미지원 |
| 수평 확장 | Simple Broker는 단일 노드 전제. 다중 노드 시 RabbitMQ STOMP Relay 필요 |
| Read Replica | 단일 DataSource 사용. 구현 없이 설계만 기술 ([design.md](docs/design.md)) |
| Presence 정확도 | TTL 기반이므로 heartbeat 중단 후 최대 300초까지 온라인으로 표시될 수 있음 |
