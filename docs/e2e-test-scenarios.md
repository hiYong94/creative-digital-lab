# End-to-End 수동 검증 시나리오

## 사전 준비

```bash
docker compose up -d
./gradlew bootRun

BASE_URL=http://localhost:8080
```

---

## 시나리오 1 — 세션 생성

```bash
curl -s -X POST $BASE_URL/sessions \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1"}' | jq .

# 기대값: 201, sessionId 포함 응답
SESSION_ID=<응답의 sessionId>
```

---

## 시나리오 2 — 세션 참여 (1:1 정원 제한)

```bash
# user2 참여 → 성공
curl -s -X POST $BASE_URL/sessions/$SESSION_ID/participants \
  -H "Content-Type: application/json" \
  -d '{"userId": "user2"}' | jq .
# 기대값: 200

# user3 참여 → 409 SESSION_CAPACITY_EXCEEDED
curl -s -X POST $BASE_URL/sessions/$SESSION_ID/participants \
  -H "Content-Type: application/json" \
  -d '{"userId": "user3"}' | jq .
# 기대값: 409, code: "SESSION_CAPACITY_EXCEEDED"

# user1 중복 참여 → 409 ALREADY_JOINED
curl -s -X POST $BASE_URL/sessions/$SESSION_ID/participants \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1"}' | jq .
# 기대값: 409, code: "ALREADY_JOINED"
```

---

## 시나리오 3 — 세션 조회

```bash
# 단건 조회
curl -s $BASE_URL/sessions/$SESSION_ID | jq .
# 기대값: 200, status: "ACTIVE"

# 목록 조회 (페이지네이션)
curl -s "$BASE_URL/sessions?page=0&size=10" | jq .
# 기대값: 200, 세션 목록

# 존재하지 않는 세션 조회 → 404
curl -s $BASE_URL/sessions/00000000-0000-0000-0000-000000000000 | jq .
# 기대값: 404, code: "SESSION_NOT_FOUND"
```

---

## 시나리오 4 — 이벤트 수집

```bash
# JOIN 이벤트
CLIENT_ID_1=$(uuidgen)
curl -s -X POST $BASE_URL/sessions/$SESSION_ID/events \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user1\",
    \"clientEventId\": \"$CLIENT_ID_1\",
    \"type\": \"JOIN\",
    \"payload\": {},
    \"sequenceNo\": 1
  }" | jq .
# 기대값: 200, eventId 포함

# MESSAGE 이벤트
CLIENT_ID_2=$(uuidgen)
curl -s -X POST $BASE_URL/sessions/$SESSION_ID/events \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user1\",
    \"clientEventId\": \"$CLIENT_ID_2\",
    \"type\": \"MESSAGE\",
    \"payload\": {\"content\": \"안녕하세요\"},
    \"sequenceNo\": 2
  }" | jq .
# 기대값: 200
```

---

## 시나리오 5 — Idempotency (멱등성)

```bash
# 동일 clientEventId 재전송 → 동일 eventId 반환
FIRST_ID=$(curl -s -X POST $BASE_URL/sessions/$SESSION_ID/events \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user1\",
    \"clientEventId\": \"$CLIENT_ID_2\",
    \"type\": \"MESSAGE\",
    \"payload\": {\"content\": \"안녕하세요\"},
    \"sequenceNo\": 2
  }" | jq -r '.id')

SECOND_ID=$(curl -s -X POST $BASE_URL/sessions/$SESSION_ID/events \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user1\",
    \"clientEventId\": \"$CLIENT_ID_2\",
    \"type\": \"MESSAGE\",
    \"payload\": {\"content\": \"안녕하세요\"},
    \"sequenceNo\": 2
  }" | jq -r '.id')

echo "첫 번째 ID: $FIRST_ID"
echo "두 번째 ID: $SECOND_ID"
# 기대값: 두 ID가 동일 (중복 저장 없음)
```

---

## 시나리오 6 — 타임라인 복원 (Full Replay)

```bash
# 이벤트 추가
for i in 3 4 5; do
  curl -s -X POST $BASE_URL/sessions/$SESSION_ID/events \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": \"user1\",
      \"clientEventId\": \"$(uuidgen)\",
      \"type\": \"MESSAGE\",
      \"payload\": {\"content\": \"메시지 $i\"},
      \"sequenceNo\": $i
    }" > /dev/null
done

# 타임라인 복원
AT=$(date -u +"%Y-%m-%dT%H:%M:%S")
curl -s "$BASE_URL/sessions/$SESSION_ID/timeline?at=$AT" | jq .
# 기대값: restoredFrom: "FULL_REPLAY", participants/messages 포함
```

---

## 시나리오 7 — Snapshot 자동 생성 + Snapshot+Delta Replay

```bash
# 이벤트 50개 수집 (50번째 이벤트에서 Snapshot 자동 생성)
for i in $(seq 6 55); do
  curl -s -X POST $BASE_URL/sessions/$SESSION_ID/events \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": \"user1\",
      \"clientEventId\": \"$(uuidgen)\",
      \"type\": \"MESSAGE\",
      \"payload\": {\"content\": \"메시지 $i\"},
      \"sequenceNo\": $i
    }" > /dev/null
  echo -n "."
done
echo ""

# 비동기 Snapshot 생성 완료 대기
sleep 2

# snapshots 테이블 확인
docker exec -it creative-digital-lab-mysql-1 \
  mysql -u creatived -pchldydrnjs1! creative-digital-lab \
  -e "SELECT COUNT(*) as snapshot_count FROM snapshots WHERE session_id = UNHEX(REPLACE('$SESSION_ID', '-', ''));"
# 기대값: snapshot_count = 1

# 타임라인 복원 → SNAPSHOT_PLUS_REPLAY 확인
AT=$(date -u +"%Y-%m-%dT%H:%M:%S")
curl -s "$BASE_URL/sessions/$SESSION_ID/timeline?at=$AT" | jq '.restoredFrom'
# 기대값: "SNAPSHOT_PLUS_REPLAY"
```

---

## 시나리오 8 — ENDED 세션 이벤트 수집 차단

```bash
# 세션 종료
curl -s -X DELETE $BASE_URL/sessions/$SESSION_ID | jq .
# 기대값: 200, status: "ENDED"

# ENDED 세션에 이벤트 수집 시도 → 422
curl -s -X POST $BASE_URL/sessions/$SESSION_ID/events \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user1\",
    \"clientEventId\": \"$(uuidgen)\",
    \"type\": \"MESSAGE\",
    \"payload\": {\"content\": \"종료 후 메시지\"},
    \"sequenceNo\": 99
  }" | jq .
# 기대값: 422, code: "INVALID_SESSION_STATE"

# ENDED 세션 참여 시도 → 422
curl -s -X POST $BASE_URL/sessions/$SESSION_ID/participants \
  -H "Content-Type: application/json" \
  -d '{"userId": "user3"}' | jq .
# 기대값: 422, code: "INVALID_SESSION_STATE"
```

---

## 시나리오 9 — WebSocket 실시간 채팅

```bash
# wscat 설치 (없으면)
npm install -g wscat

# 새 세션 생성
NEW_SESSION=$(curl -s -X POST $BASE_URL/sessions \
  -H "Content-Type: application/json" \
  -d '{"userId": "userA"}' | jq -r '.id')
echo "세션 ID: $NEW_SESSION"
```

**터미널 A — userA 연결 및 구독**

```
wscat -c "ws://localhost:8080/ws/websocket"

# STOMP CONNECT
CONNECT
accept-version:1.2
heart-beat:0,0

^@

# 브로드캐스트 구독
SUBSCRIBE
id:sub-0
destination:/topic/sessions/<NEW_SESSION>

^@
```

**터미널 B — userB 메시지 전송**

```
wscat -c "ws://localhost:8080/ws/websocket"

# CONNECT 후 메시지 전송
SEND
destination:/app/sessions/<NEW_SESSION>/events
content-type:application/json

{"userId":"userB","clientEventId":"<uuidgen>","type":"MESSAGE","payload":{"content":"안녕"},"sequenceNo":1}^@
```

```
# 기대값: 터미널 A의 /topic/sessions/{id} 구독에 메시지 수신
```

---

## 시나리오 10 — 재연결 + 누락 이벤트 수신

```bash
# 1. 터미널 A에서 userA 연결 끊음 (Ctrl+C)
#    → 서버 로그에 DISCONNECT 이벤트 수집 확인

# 2. 연결 끊김 동안 이벤트 수집
for i in 2 3 4; do
  curl -s -X POST $BASE_URL/sessions/$NEW_SESSION/events \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": \"userB\",
      \"clientEventId\": \"$(uuidgen)\",
      \"type\": \"MESSAGE\",
      \"payload\": {\"content\": \"누락 메시지 $i\"},
      \"sequenceNo\": $i
    }" > /dev/null
done

# 3. userA 재연결 후 아래 순서로 전송
```

```
wscat -c "ws://localhost:8080/ws/websocket"

# CONNECT
CONNECT
accept-version:1.2
heart-beat:0,0

^@

# 누락 이벤트 구독
SUBSCRIBE
id:sub-missed
destination:/user/queue/missed

^@

# 재연결 요청 (마지막으로 받은 sequenceNo 이후 요청)
SEND
destination:/app/sessions/<NEW_SESSION>/reconnect
content-type:application/json

{"userId":"userA","clientEventId":"<uuidgen>","resumeFromSequenceNo":1}^@

# 기대값: /user/queue/missed 로 누락 메시지 2, 3, 4 수신
```

---

## 시나리오 11 — 마지막 참여자 퇴장 시 세션 자동 종료

```bash
# 새 세션 생성 후 두 명 참여
AUTO_SESSION=$(curl -s -X POST $BASE_URL/sessions \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1"}' | jq -r '.id')

curl -s -X POST $BASE_URL/sessions/$AUTO_SESSION/participants \
  -H "Content-Type: application/json" \
  -d '{"userId": "user2"}' > /dev/null

# user1 LEAVE
curl -s -X POST $BASE_URL/sessions/$AUTO_SESSION/events \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user1\",
    \"clientEventId\": \"$(uuidgen)\",
    \"type\": \"LEAVE\",
    \"payload\": {},
    \"sequenceNo\": 1
  }" > /dev/null

# user2 LEAVE (마지막 참여자 → 세션 자동 종료)
curl -s -X POST $BASE_URL/sessions/$AUTO_SESSION/events \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"user2\",
    \"clientEventId\": \"$(uuidgen)\",
    \"type\": \"LEAVE\",
    \"payload\": {},
    \"sequenceNo\": 2
  }" > /dev/null

# 세션 상태 확인
curl -s $BASE_URL/sessions/$AUTO_SESSION | jq '.status'
# 기대값: "ENDED"
```

---

## 체크리스트

| # | 시나리오 | 검증 항목 | 결과 |
|---|----------|-----------|------|
| 1 | 세션 생성 | 201 응답, sessionId 포함 | |
| 2 | 정원 제한 | 3번째 참여 → 409 / 중복 참여 → 409 | |
| 3 | 세션 조회 | 존재하지 않는 세션 → 404 | |
| 4 | 이벤트 수집 | 200 응답, eventId 포함 | |
| 5 | Idempotency | 동일 clientEventId 재전송 → 동일 eventId | |
| 6 | Full Replay | restoredFrom: "FULL_REPLAY" | |
| 7 | Snapshot+Delta | 50개 이후 → restoredFrom: "SNAPSHOT_PLUS_REPLAY" | |
| 8 | ENDED 세션 차단 | 이벤트 수집/참여 시도 → 422 | |
| 9 | WebSocket 브로드캐스트 | 메시지 전송 → 상대방 수신 | |
| 10 | 재연결 누락 이벤트 | /user/queue/missed 로 누락 이벤트 수신 | |
| 11 | 자동 세션 종료 | 마지막 참여자 퇴장 → status: "ENDED" | |
