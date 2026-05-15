# 이벤트 소싱 규칙

## 이벤트 불변성

- `chat_events` 테이블은 Append-only. UPDATE·DELETE 절대 금지.
- 저장 후 수정·삭제가 필요한 상황은 새 이벤트(보정 이벤트)로 표현한다.

## 이벤트 타입

| 타입 | 트리거 | Projection 효과 |
|------|--------|----------------|
| `JOIN` | 참여자 입장 | `participants`에 추가 |
| `LEAVE` | 참여자 정상 퇴장 | `participants`에서 제거 |
| `MESSAGE` | 메시지 전송 | `messages`에 추가 |
| `DISCONNECT` | 연결 끊김 (비정상) | 참여자 오프라인 상태로 마킹 |
| `RECONNECT` | 재연결 | 참여자 온라인 상태로 복원 |

## 이벤트 순서 기준

- 정렬 1순위: `server_received_at ASC` (서버 수신 시각)
- 정렬 2순위: `sequence_no ASC` (동일 시각 tie-break)
- 클라이언트 시계(`client_sent_at`)는 정렬에 사용하지 않는다.

## 필수 필드

| 필드 | 이유 |
|------|------|
| `clientEventId` | Idempotency 보장 — 없으면 수집 거부 |
| `sequence_no` | 순서 복원에 사용 — 없으면 수집 거부 |
| `server_received_at` | 정렬 1순위 — 서버가 채번 |

## Projection 전략

### Full Replay
- 이벤트 전체를 처음부터 순서대로 재생해 `SessionState`를 도출.
- Snapshot이 없을 때 또는 복원 정확성 검증 시 사용.

### Snapshot + Delta Replay
- 가장 최근 Snapshot 시점까지 건너뛰고, 이후 이벤트만 재생.
- Snapshot이 없으면 자동으로 Full Replay로 폴백.
- 성능 최적화 목적. Snapshot 없이도 항상 Full Replay로 복원 가능해야 한다.

### Projection 중 중복 이벤트 처리

- `sequence_no`가 이미 적용된 이벤트와 겹치면 해당 이벤트를 건너뛴다.
- Snapshot 포함 이후 이벤트에도 동일 규칙 적용.

## Snapshot 생성 조건

- 이벤트 50개 단위마다 자동 생성.
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 비동기 생성.
- Snapshot 생성 실패는 `WARN` 로그. 비즈니스 흐름을 중단시키지 않는다.
- Snapshot 없이도 Full Replay로 복원 가능한 상태를 항상 유지해야 한다.

## EventProjector 확장 원칙 (OCP)

- 새 이벤트 타입 추가 = `EventProjector` 구현 클래스 추가만으로 해결.
- 기존 Projector 수정 금지.
- `EventProjectorRegistry`에 새 구현체를 등록하는 것으로 완료.

## 타임라인 복원 API

- `GET /sessions/{id}/timeline?at=<ISO8601>` — 특정 시점 상태 반환.
- `at` 이하 이벤트만 Replay. 이후 이벤트는 제외.
- Snapshot + Delta 전략 적용 가능.
