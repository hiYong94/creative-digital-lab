package com.creatived.chat.domain.session;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Session {

    private final SessionId id;
    private SessionStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime endedAt;
    private final List<Participant> participants;

    public Session(SessionId id, LocalDateTime createdAt) {
        this.id = id;
        this.status = SessionStatus.ACTIVE;
        this.createdAt = createdAt;
        this.participants = new ArrayList<>();
    }

    public Session(SessionId id, SessionStatus status, LocalDateTime createdAt, LocalDateTime endedAt, List<Participant> participants) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.endedAt = endedAt;
        this.participants = new ArrayList<>(participants);
    }

    public Participant join(String userId, LocalDateTime now) {
        requireActive();
        boolean alreadyJoined = participants.stream()
                .anyMatch(p -> p.getUserId().equals(userId) && !p.hasLeft());
        if (alreadyJoined) {
            throw new AlreadyJoinedException(userId);
        }
        Participant participant = new Participant(ParticipantId.create(), id, userId, now);
        participants.add(participant);
        return participant;
    }

    public void end(LocalDateTime now) {
        requireActive();
        this.status = SessionStatus.ENDED;
        this.endedAt = now;
    }

    public void requireActive() {
        if (status != SessionStatus.ACTIVE) {
            throw new InvalidSessionStateException("세션이 이미 종료되었습니다. id=" + id);
        }
    }

    public SessionId getId() { return id; }
    public SessionStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public List<Participant> getParticipants() { return Collections.unmodifiableList(participants); }
}
