# 관찰 가능성 규칙

## MDC (Mapped Diagnostic Context)

- `@UseCase` AOP가 메서드 진입 시 MDC에 `traceId`, `sessionId`, `useCase` 자동 주입.
- `traceId`는 에러 응답의 `traceId` 필드와 동일값. 로그-응답 연계에 사용.
- MDC는 메서드 종료 시 반드시 정리 (`finally` 블록).

## 로그 레벨 기준

| 레벨 | 사용 시점 |
|------|----------|
| `INFO` | 비즈니스 이벤트 (세션 생성, 이벤트 수집 등) |
| `WARN` | 복구 가능한 오류 (Snapshot 생성 실패, 중복 이벤트 등) |
| `ERROR` | 복구 불가 오류, 예상치 못한 예외 |
| `DEBUG` | 개발 환경 전용. 프로덕션에서는 비활성화. |

- 서비스 메서드에 직접 로그 작성 금지. `UseCaseLoggingAspect`에서 일괄 처리.

## Micrometer 메트릭

- `Counter`: 이벤트 수집 횟수, 중복 차단 횟수, 에러 횟수
- `Timer`: 이벤트 수집 처리 시간, 타임라인 복원 시간
- `Gauge`: 활성 세션 수, 현재 WebSocket 연결 수
- 메트릭 태그: `session_id`, `event_type`, `status` (success/failure)

## 헬스체크

- `/actuator/health` — DB, Redis 커넥션 상태 포함.
- 커스텀 헬스 인디케이터 추가 시 `HealthIndicator` 구현.

## 슬로우 쿼리 감지

- HikariCP `connectionTimeout` 초과 시 `WARN` 로그.
- JPA `spring.jpa.show-sql`은 개발 환경에서만 활성화.
