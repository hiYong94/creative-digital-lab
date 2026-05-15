# 테스트 규칙

## 원칙

- 비즈니스 로직 테스트는 Fake 구현체를 활용한 단위 테스트로 커버한다.
- **핵심 비즈니스 규칙**에 집중한다. 단순 위임·getter·생성자는 테스트하지 않는다.

## 테스트 계층

| 계층 | 대상 | 도구 |
|------|------|------|
| 단위 테스트 | Domain 로직, Projection | JUnit 5, 순수 Java |
| 단위 테스트 | Application Service | JUnit 5, Fake Repository |

## 테스트 케이스 유형

해피 케이스 중심을 지양한다. 아래 7가지 유형을 기준으로 케이스를 설계한다.

### 1. Mirror case — 경계 양쪽을 쌍으로 검증
- ACTIVE 세션 참여 성공 ↔ ENDED 세션 참여 실패
- 첫 번째 `clientEventId` 저장 ↔ 동일 `clientEventId` 재요청 시 저장 없이 반환

### 2. Boundary case — 조건이 변하는 경계값
- 이벤트 49개 → Snapshot 미생성 / 50개 → 생성 / 51개 → 미생성
- timeline `at`이 이벤트 `serverReceivedAt`과 정확히 일치할 때 포함 여부

### 3. State transition case — 유효·무효 상태 전이 모두 검증
- `ACTIVE → ENDED` 성공
- `ENDED → ENDED` 재종료 → 예외
- `ENDED` 상태에서 참여자 추가·이벤트 수집 시도 → 예외

### 4. Sequence case — 연산 순서가 결과에 영향을 주는 시나리오
- `JOIN → LEAVE → JOIN` 순서 Projection 시 최종 참여자 목록
- Snapshot 생성 전·후 이벤트의 Delta Replay 경계 처리

### 5. Invariant case — 여러 연산 후에도 비즈니스 불변 규칙이 유지됨
- JOIN 이벤트 중복 수신 시 참여자 목록에 중복 등장하지 않음
- Snapshot+Delta 결과 == Full Replay 결과 (복원 전략 무관)
- `sequenceNo`와 무관하게 `serverReceivedAt` 기준 정렬이 유지됨

### 6. Empty/Minimal case — 데이터가 없거나 최소인 상태
- 이벤트 0개일 때 timeline 복원 → 빈 `SessionState` 반환
- Snapshot 없을 때 Full Replay 폴백 동작
- 참여자 없는 세션에서 LEAVE 이벤트 Projection

### 7. Idempotency case — 반복 실행해도 결과가 동일함
- 동일 `CollectEventCommand` 3회 호출 시 저장된 이벤트는 1개
- 동일 JOIN 이벤트를 `SessionState`에 두 번 apply해도 참여자 수 불변

## Fake 전략

- `SessionRepository`, `ChatEventRepository`, `SnapshotRepository` 각각에 In-Memory Fake 구현체 작성.
- Fake는 `test` 소스셋에만 위치. 프로덕션 코드에 노출하지 않는다.
- Fake는 도메인 규칙을 검증하지 않는다. 저장·조회 동작만 구현한다.

## 단위 테스트 대상

- `EventProjector` 구현체별 상태 전이 검증.
- `Session` 도메인 메서드 (상태 검증, 참여자 추가/제거).
- `SnapshotApplicationService.restoreAt()` — Snapshot+Delta / Full Replay 경로 분기.
- Idempotency 흐름 — Fake `IdempotencyKeyStore`로 중복 감지 검증.

## 테스트 픽스처

- 공통 픽스처 팩토리 메서드를 테스트 전용 유틸 클래스에 집중.
- 테스트 내 `new SessionId(UUID.randomUUID())` 직접 생성은 허용.
