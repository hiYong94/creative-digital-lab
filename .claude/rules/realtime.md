# 실시간 통신 규칙

## WebSocket / STOMP 구조

- 클라이언트는 STOMP over WebSocket으로 연결.
- 브로커: Simple Broker (단일 노드). 수평 확장 시 RabbitMQ STOMP Relay로 교체 — README에 설계 기술.
- Spring의 STOMP Broker Relay는 Redis가 아닌 RabbitMQ/ActiveMQ 전용임에 유의.

## 채널 설계

| 방향 | 경로 | 용도 |
|------|------|------|
| 클라이언트 → 서버 | `/app/sessions/{id}/events` | 이벤트 전송 |
| 서버 → 클라이언트 | `/topic/sessions/{id}` | 이벤트 브로드캐스트 |
| 서버 → 특정 클라이언트 | `/user/queue/missed` | 누락 이벤트 재전송 |

## Presence (접속 상태)

- Redis Hash 키: `session:{id}:presence`
- 필드: `participantId` → 마지막 heartbeat 타임스탬프
- TTL: 300초. 갱신 주기: heartbeat 수신 시마다.
- TTL 만료 = 오프라인 간주. 별도 상태 필드를 두지 않는다.
- Redis를 영구 저장소로 사용하지 않는다 — Presence는 휘발성 데이터.

## Reconnect / 누락 이벤트 재전송

- 클라이언트는 재연결 시 마지막으로 수신한 `resumeFromSequenceNo`를 전송.
- 서버는 해당 `sequence_no` 이후 이벤트를 조회해 `/user/queue/missed`로 전송.
- RECONNECT 이벤트도 별도로 수집해 상태 복원에 반영한다.

## 연결 끊김 처리

- 정상 퇴장: 클라이언트가 LEAVE 이벤트 명시적 전송.
- 비정상 끊김: 서버가 WebSocket `SessionDisconnectEvent` 감지 → DISCONNECT 이벤트 자동 수집.
- DISCONNECT 이벤트는 서버가 생성하므로 `clientEventId` 없이 예외적으로 허용.

## 이벤트 브로드캐스트 시점

- 이벤트 DB 저장 완료 후 브로드캐스트.
- 저장 전 브로드캐스트하면 수신 측이 복원 시 해당 이벤트를 찾지 못할 수 있다.

## 메시지 크기 제한

- 단일 WebSocket 메시지 최대 크기: 64KB.
- 초과 시 클라이언트에 오류 프레임 반환.
