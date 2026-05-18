package com.creatived.chat.application.snapshot;

import com.creatived.chat.application.event.EventCollectedEvent;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.infrastructure.config.SnapshotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SnapshotEventListenerTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);

    private FakeChatEventRepository chatEventRepository;
    private SnapshotApplicationService snapshotApplicationService;
    private SnapshotEventListener listener;
    private SessionId sessionId;

    @BeforeEach
    void setUp() {
        chatEventRepository = new FakeChatEventRepository();
        snapshotApplicationService = mock(SnapshotApplicationService.class);
        SnapshotProperties properties = new SnapshotProperties(50, true);

        listener = new SnapshotEventListener(chatEventRepository, snapshotApplicationService, properties);
        sessionId = SessionId.create();
    }

    // Boundary case: 49개 → interval 미달, Snapshot 미생성
    @Test
    void onEventCollected_49events_noSnapshot() {
        saveEvents(49);

        listener.onEventCollected(makeEvent(49));

        verify(snapshotApplicationService, never()).createSnapshot(any());
    }

    // Boundary case: 50개 → interval 정확히 달성, Snapshot 생성
    @Test
    void onEventCollected_50events_createsSnapshot() {
        saveEvents(50);

        listener.onEventCollected(makeEvent(50));

        verify(snapshotApplicationService).createSnapshot(sessionId);
    }

    // Boundary case: 51개 → interval 초과, Snapshot 미생성
    @Test
    void onEventCollected_51events_noSnapshot() {
        saveEvents(51);

        listener.onEventCollected(makeEvent(51));

        verify(snapshotApplicationService, never()).createSnapshot(any());
    }

    private void saveEvents(int count) {
        for (int i = 1; i <= count; i++) {
            chatEventRepository.save(new ChatEvent(
                    ChatEventId.create(),
                    sessionId,
                    "user1",
                    ClientEventId.of(UUID.randomUUID().toString()),
                    EventType.MESSAGE,
                    Map.of(),
                    i,
                    BASE.plusMinutes(i)
            ));
        }
    }

    private EventCollectedEvent makeEvent(long seqNo) {
        ChatEvent chatEvent = new ChatEvent(
                ChatEventId.create(),
                sessionId,
                "user1",
                ClientEventId.of(UUID.randomUUID().toString()),
                EventType.MESSAGE,
                Map.of(),
                seqNo,
                BASE.plusMinutes(seqNo)
        );
        return new EventCollectedEvent(chatEvent);
    }
}
