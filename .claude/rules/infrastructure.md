# 인프라 규칙

## MySQL

- UUID는 `BINARY(16)` 저장. 조회 성능과 저장 공간 최적화.
- 타임스탬프는 `DATETIME(6)` (마이크로초 정밀도).
- 이벤트 메타데이터 등 구조가 유동적인 데이터는 `JSON` 컬럼 사용.
- 중복 이벤트 차단: `clientEventId`에 UNIQUE 제약 + `INSERT IGNORE` 조합.
- 기본 격리 수준: `REPEATABLE READ` (MySQL 기본값 유지).

## Read Replica 라우팅

- `@Transactional(readOnly = true)` → `DataSourceRoutingAspect`가 Read Replica로 자동 라우팅.
- `@Transactional` (쓰기) → Primary로 라우팅.
- `DataSourceContextHolder`로 현재 라우팅 대상을 ThreadLocal에 보관.

## Redis

- 용도: Idempotency 키 캐시(TTL 10분), Presence Hash(TTL 300초), STOMP Broker Relay.
- 영구 저장 목적으로 사용 금지. 재시작 시 유실을 전제로 설계.
- Idempotency 키: `idempotency:{clientEventId}`. TTL 만료 후 재처리 가능.

## Idempotency — 이중 방어

1. **Redis**: 이벤트 수집 시 `clientEventId` 키 존재 여부 확인. 존재하면 즉시 200 반환.
2. **MySQL UNIQUE**: Redis TTL 만료 후 재요청이 DB에 도달해도 UNIQUE 위반으로 차단.
- 두 방어 중 하나라도 통과되면 이벤트는 저장되지 않는다.

## 비동기 처리

- `ThreadPoolTaskExecutor` 사용. `SimpleAsyncTaskExecutor` 금지 (스레드 무제한 생성).
- Executor 설정: `AsyncConfig`에서 중앙 관리.
- 스레드 풀 크기, 큐 용량은 Properties로 외부화.

## 커넥션 풀

- HikariCP 사용 (Spring Boot 기본).
- 풀 크기는 `application.yml`에서 환경별로 분리.

## 설정 외부화

- 환경별 설정은 `application-{profile}.yml`로 분리.
- 비밀정보(DB 비밀번호, Redis 비밀번호 등)는 환경 변수로 주입. 소스 커밋 금지.

## 트랜잭션 경계

- `@Transactional`은 Application Service 메서드에만 선언.
- Repository, Controller에 `@Transactional` 선언 금지.
