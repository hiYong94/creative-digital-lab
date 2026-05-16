package com.creatived.chat.domain.session;

import java.time.LocalDateTime;

public class Participant {

    private final ParticipantId id;
    private final SessionId sessionId;
    private final String userId;
    private final LocalDateTime joinedAt;
    private LocalDateTime leftAt;

    public Participant(ParticipantId id, SessionId sessionId, String userId, LocalDateTime joinedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    public void leave(LocalDateTime leftAt) {
        this.leftAt = leftAt;
    }

    public boolean hasLeft() {
        return leftAt != null;
    }

    public ParticipantId getId() { return id; }
    public SessionId getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getLeftAt() { return leftAt; }
}
