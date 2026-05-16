package com.creatived.chat.domain.event;

import com.creatived.chat.domain.session.SessionId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatEventRepository {

    ChatEvent save(ChatEvent event);

    Optional<ChatEvent> findBySessionIdAndClientEventId(SessionId sessionId, ClientEventId clientEventId);

    List<ChatEvent> findBySessionIdOrderByServerReceivedAtAscSequenceNoAsc(SessionId sessionId);

    List<ChatEvent> findBySessionIdAndServerReceivedAtBeforeOrEqual(SessionId sessionId, LocalDateTime at);

    List<ChatEvent> findBySessionIdAndSequenceNoAfter(SessionId sessionId, long sequenceNo);

    long countBySessionId(SessionId sessionId);
}
