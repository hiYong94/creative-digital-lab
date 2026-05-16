package com.creatived.chat.domain.event;

import com.creatived.chat.domain.session.SessionId;

import java.time.LocalDateTime;
import java.util.Map;

public class ChatEvent {

    private final ChatEventId id;
    private final SessionId sessionId;
    private final String userId;
    private final ClientEventId clientEventId;
    private final EventType type;
    private final Map<String, Object> payload;
    private final long sequenceNo;
    private final LocalDateTime serverReceivedAt;

    public ChatEvent(ChatEventId id, SessionId sessionId, String userId, ClientEventId clientEventId,
                     EventType type, Map<String, Object> payload, long sequenceNo, LocalDateTime serverReceivedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.clientEventId = clientEventId;
        this.type = type;
        this.payload = Map.copyOf(payload);
        this.sequenceNo = sequenceNo;
        this.serverReceivedAt = serverReceivedAt;
    }

    public ChatEventId getId() { return id; }
    public SessionId getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public ClientEventId getClientEventId() { return clientEventId; }
    public EventType getType() { return type; }
    public Map<String, Object> getPayload() { return payload; }
    public long getSequenceNo() { return sequenceNo; }
    public LocalDateTime getServerReceivedAt() { return serverReceivedAt; }
}
