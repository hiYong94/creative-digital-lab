package com.creatived.chat.domain.snapshot;

import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.session.SessionId;

import java.time.LocalDateTime;
import java.util.UUID;

public class Snapshot {

    private final UUID id;
    private final SessionId sessionId;
    private final ChatEventId lastEventId;
    private final SessionState state;
    private final long lastSequenceNo;
    private final LocalDateTime createdAt;

    public Snapshot(UUID id, SessionId sessionId, ChatEventId lastEventId,
                    SessionState state, long lastSequenceNo, LocalDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.lastEventId = lastEventId;
        this.state = state;
        this.lastSequenceNo = lastSequenceNo;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public SessionId getSessionId() { return sessionId; }
    public ChatEventId getLastEventId() { return lastEventId; }
    public SessionState getState() { return state; }
    public long getLastSequenceNo() { return lastSequenceNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
