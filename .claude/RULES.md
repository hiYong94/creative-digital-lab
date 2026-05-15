# Rules

## 문서 규칙

이 프로젝트의 모든 규칙 문서는 아래 5가지 원칙을 따른다.

1. **기본 상식 제거** — 프레임워크·언어 수준의 일반 지식은 작성하지 않는다. 이 프로젝트의 결정사항만 기록한다.
2. **모듈화 + 파일 참조** — 주제별로 파일을 분리하고 `@import`로 연결한다. 하나의 파일에 모든 것을 담지 않는다.
3. **예시는 필요한 경우에만** — 복잡도·이해도가 높은 경우에만 스니펫을 사용한다. 소스 파일이 존재하면 파일 경로로 참조한다.
4. **500줄 미만 유지** — 초과 시 즉시 검토하고 불필요한 내용을 삭제한다.
5. **명확한 헤딩과 글머리 기호** — 에이전트가 구조를 빠르게 파악할 수 있도록 한다.

---

## 절대 금지

| 금지 | 이유 |
|------|------|
| `domain`에 JPA 코드(`@Entity`) | DB 기술 변경 시 비즈니스 로직 영향 |
| `Controller` / `Repository`에 `@Transactional` | 트랜잭션 경계는 Application Service가 결정 |
| `chat_events` UPDATE / DELETE | 이벤트 로그는 Append-only. 불변성이 복원 정확성을 보장 |
| `clientEventId` 없는 이벤트 수집 허용 | Idempotency 불가 → 데이터 오염 |
| `sequenceNo` 없는 이벤트 허용 | 순서 결정 불가 → 복원 결과 불일치 |
| 클라이언트 시계를 정렬 1순위로 사용 | 기기마다 시계가 달라 순서 보장 불가 |
| Redis를 영구 저장소로 사용 | 재시작 시 데이터 유실 |
| `SimpleAsyncTaskExecutor` 사용 | 스레드 무제한 생성 → OOM |

---

@.claude/rules/architecture.md
@.claude/rules/event-sourcing.md
@.claude/rules/api.md
@.claude/rules/realtime.md
@.claude/rules/infrastructure.md
@.claude/rules/observability.md
@.claude/rules/testing.md
