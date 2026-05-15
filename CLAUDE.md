# 크리에이티브 디지털 랩 — 백엔드 사전 과제

1:1 실시간 채팅 + Event Sourcing 기반 상태 복원 서비스.

## 기술 스택

- Spring Boot 4.0.6 / Java 17 / Gradle
- MySQL (BINARY(16) UUID, DATETIME(6))
- Redis (Presence, Idempotency Key)
- WebSocket (STOMP + Redis Broker Relay)
- JPA (Domain/Infrastructure Entity 분리)
- SpringDoc OpenAPI 3 (Swagger)

## 규칙

@.claude/RULES.md

## 코드 패턴

코드를 생성할 때 `.claude/skills/code-patterns/SKILL.md` 의 패턴을 참조한다.

## Git 컨벤션

@docs/git-convention.md
