package com.creatived.chat.application.event;

import com.creatived.chat.application.session.FakeSessionRepository;
import com.creatived.chat.application.snapshot.FakeChatEventRepository;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.session.InvalidSessionStateException;
import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EventApplicationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    private FakeSessionRepository sessionRepository;
    private FakeChatEventRepository chatEventRepository;
    private EventApplicationService service;
    private SessionId sessionId;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeSessionRepository();
        chatEventRepository = new FakeChatEventRepository();
        service = new EventApplicationService(
                chatEventRepository, sessionRepository, mock(ApplicationEventPublisher.class));

        Session session = new Session(SessionId.create(), NOW);
        sessionRepository.save(session);
        sessionId = session.getId();
    }

    // State transition: ENDED 세션에 이벤트 수집 시도 → 422
    @Test
    void collect_endedSession_throwsInvalidSessionStateException() {
        Session session = sessionRepository.findById(sessionId).orElseThrow();
        session.end(NOW);

        CollectEventCommand command = new CollectEventCommand(
                sessionId.value(), "user1", UUID.randomUUID().toString(),
                EventType.MESSAGE, Map.of(), 1L
        );

        assertThatThrownBy(() -> service.collect(command))
                .isInstanceOf(InvalidSessionStateException.class);
    }
}
