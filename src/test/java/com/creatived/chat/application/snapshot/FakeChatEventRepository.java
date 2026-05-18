package com.creatived.chat.application.snapshot;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventRepository;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.session.SessionId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FakeChatEventRepository implements ChatEventRepository {

    private final List<ChatEvent> store = new ArrayList<>();

    @Override
    public ChatEvent save(ChatEvent event) {
        store.add(event);
        return event;
    }

    @Override
    public Optional<ChatEvent> findBySessionIdAndClientEventId(SessionId sessionId, ClientEventId clientEventId) {
        return store.stream()
                .filter(e -> e.getSessionId().equals(sessionId) && e.getClientEventId().equals(clientEventId))
                .findFirst();
    }

    @Override
    public List<ChatEvent> findBySessionIdOrderByServerReceivedAtAscSequenceNoAsc(SessionId sessionId) {
        return store.stream()
                .filter(e -> e.getSessionId().equals(sessionId))
                .sorted(Comparator.comparing(ChatEvent::getServerReceivedAt)
                        .thenComparingLong(ChatEvent::getSequenceNo))
                .toList();
    }

    @Override
    public List<ChatEvent> findBySessionIdAndServerReceivedAtBeforeOrEqual(SessionId sessionId, LocalDateTime at) {
        return store.stream()
                .filter(e -> e.getSessionId().equals(sessionId))
                .filter(e -> !e.getServerReceivedAt().isAfter(at))
                .sorted(Comparator.comparing(ChatEvent::getServerReceivedAt)
                        .thenComparingLong(ChatEvent::getSequenceNo))
                .toList();
    }

    @Override
    public List<ChatEvent> findBySessionIdAndSequenceNoAfter(SessionId sessionId, long sequenceNo) {
        return store.stream()
                .filter(e -> e.getSessionId().equals(sessionId))
                .filter(e -> e.getSequenceNo() > sequenceNo)
                .sorted(Comparator.comparing(ChatEvent::getServerReceivedAt)
                        .thenComparingLong(ChatEvent::getSequenceNo))
                .toList();
    }

    @Override
    public long countBySessionId(SessionId sessionId) {
        return store.stream().filter(e -> e.getSessionId().equals(sessionId)).count();
    }
}
