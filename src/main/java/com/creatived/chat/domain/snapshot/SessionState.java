package com.creatived.chat.domain.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SessionState {

    private final Set<String> participants;
    private final List<MessageView> messages;
    private final long lastSequenceNo;

    private SessionState(Set<String> participants, List<MessageView> messages, long lastSequenceNo) {
        this.participants = Collections.unmodifiableSet(new LinkedHashSet<>(participants));
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.lastSequenceNo = lastSequenceNo;
    }

    public static SessionState empty() {
        return new SessionState(new LinkedHashSet<>(), new ArrayList<>(), 0L);
    }

    public SessionState withParticipantAdded(String userId) {
        Set<String> next = new LinkedHashSet<>(participants);
        next.add(userId);
        return new SessionState(next, messages, lastSequenceNo);
    }

    public SessionState withParticipantRemoved(String userId) {
        Set<String> next = new LinkedHashSet<>(participants);
        next.remove(userId);
        return new SessionState(next, messages, lastSequenceNo);
    }

    public SessionState withMessageAdded(MessageView message) {
        List<MessageView> next = new ArrayList<>(messages);
        next.add(message);
        return new SessionState(participants, next, lastSequenceNo);
    }

    public SessionState withLastSequenceNo(long sequenceNo) {
        return new SessionState(participants, messages, sequenceNo);
    }

    public Set<String> getParticipants() { return participants; }
    public List<MessageView> getMessages() { return messages; }
    public long getLastSequenceNo() { return lastSequenceNo; }
}
