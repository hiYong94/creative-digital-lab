# Git 컨벤션

본 문서는 크리에이티브 디지털 랩 백엔드 과제 진행 중 적용할 Git 컨벤션을 정의한다.
1인 작업이지만 의도가 드러나는 히스토리를 남기기 위해 일관된 규칙을 적용한다.

---

## 브랜치 네이밍

```
<type>/<short-description>
```

| type     | 사용 시점                |
| -------- | ------------------------ |
| feat     | 새 기능                  |
| fix      | 버그 수정                |
| refactor | 동작 변경 없는 코드 개선 |
| chore    | 빌드, 설정, 의존성       |
| docs     | 문서, 주석               |
| test     | 테스트 코드              |

**본 과제 사용 예**

```
chore/spring-boot-init
feat/session-crud
feat/websocket-stomp
feat/event-collect
feat/idempotency
feat/timeline-restore
feat/snapshot-projection
feat/reconnect-resume
refactor/ddd-layer-separation
test/integration-testcontainers
docs/readme-design
```

---

## 커밋 메시지

```
<type>: <제목>          ← 50자 이내, 마침표 없음
                        ← 한 줄 공백
[섹션]                  ← 변경 영역 카테고리 (도메인, API, 예외처리, 인프라 등)
- Why 중심 서술         ← 무엇을 했는지보다 왜 했는지 위주
  - 세부 항목           ← 필요 시 들여쓰기로 계층 표현
```

- **제목**: 한국어. 기술 용어(Spring Boot, WebSocket, STOMP, Redis, JPA, Snapshot, Projection, DLQ, Idempotency 등)는 영어 허용
- **본문**: 생략 가능. 변경이 많을 경우 섹션 헤더(`[도메인]`, `[API]`, `[예외처리]`, `[인프라]` 등)로 카테고리를 나누고, 각 항목은 *왜 그렇게 결정했는지 / 트레이드오프 / 의도*를 위주로 작성
- **들여쓰기**: 섹션 내에서 부모-자식 관계가 있을 때 2칸 들여쓰기로 계층 표현
- **트레일러 미사용**: `Co-Authored-By` 등 자동 생성 트레일러는 추가하지 않는다

**본 과제 사용 예**

```
chore: Spring Boot 프로젝트 초기 세팅 및 의존성 구성
feat: Session 생성·참여·종료 REST API 추가
feat: WebSocket STOMP 엔드포인트 및 메시지 핸들러 구현
feat: 이벤트 수집 API에 Idempotency 적용
feat: clientEventId 기반 중복 이벤트 차단 (Redis + MySQL 이중 방어)
feat: GET /sessions/{id}/timeline 특정 시점 상태 복원 API 추가
feat: Snapshot + Delta Replay 기반 복원 최적화
feat: RECONNECT 이벤트 처리 및 누락 이벤트 재전송
refactor: Domain/Infrastructure JPA Entity 레이어 분리
test: Testcontainers 기반 중복 이벤트 차단 통합 테스트 추가
docs: README에 아키텍처 설계 및 트레이드오프 기술
```

**본문 작성 예**

```
feat: Session 생성·참여·종료 REST API 추가 (Phase 3)

[도메인]
- 1:1 채팅 제약을 클라이언트가 아닌 도메인에서 강제해 규칙 위반 불가
  - SessionCapacityExceededException: 3번째 참여자 시도 시 409
- 강제 종료 시 leftAt 자동 설정으로 세션-참여자 간 DB 정합성 보장

[API]
- join() 이중 트랜잭션을 단일로 통합해 불필요한 DB 조회 제거

[예외처리]
- @Validated 누락으로 동작하지 않던 @RequestParam 제약 실제 적용
- UUID 형식 오류 시 500 → 400 처리
```

---

## PR

1인 작업이지만 작업 단위 분리와 의사결정 기록을 위해 가능한 한 PR로 진행한다.

**제목**: 커밋 메시지와 동일한 형식 (`<type>: <제목>`)

**본문 템플릿**

```markdown
## 작업 내용
- 변경한 것을 bullet로 간략히

## 의도 / 배경 (선택)
- 왜 이 방식을 선택했는지, 다른 옵션과의 트레이드오프

## 특이사항 (선택)
- 후속 작업, 알려진 한계, 추가 검증이 필요한 부분
```

---

## Merge 전략

- **Merge commit 방식** (GitHub UI에서 수동 merge)
- feature 브랜치의 커밋이 `main`에 그대로 남는 구조

---

## 본 과제에 한정한 추가 규칙

- 모든 작업은 `main`이 아닌 feature 브랜치에서 진행 후 merge한다
- 비밀정보(`.env`, 자격 증명, application-secret.yml 등)는 커밋하지 않는다
- AI 도구 사용은 허용되지만, 모든 커밋의 의사결정을 본인이 설명할 수 있어야 한다 (과제 명세 명시)
