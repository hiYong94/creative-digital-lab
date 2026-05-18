package com.creatived.chat.application.snapshot;

import com.creatived.chat.application.session.FakeSessionRepository;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.snapshot.EventProjectorRegistry;
import com.creatived.chat.domain.snapshot.SessionState;
import com.creatived.chat.domain.snapshot.Snapshot;
import com.creatived.chat.domain.snapshot.projector.DisconnectProjector;
import com.creatived.chat.domain.snapshot.projector.JoinProjector;
import com.creatived.chat.domain.snapshot.projector.LeaveProjector;
import com.creatived.chat.domain.snapshot.projector.MessageProjector;
import com.creatived.chat.domain.snapshot.projector.ReconnectProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotApplicationServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);

    private FakeChatEventRepository chatEventRepository;
    private FakeSnapshotRepository snapshotRepository;
    private FakeSessionRepository sessionRepository;
    private SnapshotApplicationService service;

    private SessionId sessionId;

    @BeforeEach
    void setUp() {
        chatEventRepository = new FakeChatEventRepository();
        snapshotRepository = new FakeSnapshotRepository();
        sessionRepository = new FakeSessionRepository();

        EventProjectorRegistry registry = new EventProjectorRegistry(List.of(
                new JoinProjector(), new LeaveProjector(), new MessageProjector(),
                new DisconnectProjector(), new ReconnectProjector()
        ));

        service = new SnapshotApplicationService(
                chatEventRepository, snapshotRepository, sessionRepository, registry);

        sessionId = SessionId.create();
        Session session = new Session(sessionId, BASE);
        sessionRepository.save(session);
    }

    // Empty case: 이벤트 0개 → 빈 SessionState 반환
    @Test
    void restoreAt_noEvents_returnsEmptyState() {
        TimelineResult result = service.restoreAt(new RestoreTimelineQuery(sessionId.value(), BASE.plusHours(1)));

        assertThat(result.state().getParticipants()).isEmpty();
        assertThat(result.state().getMessages()).isEmpty();
        assertThat(result.restoredFrom()).isEqualTo("FULL_REPLAY");
    }

    // Sequence case: JOIN → MESSAGE → LEAVE → JOIN 순서 재생 결과 검증
    @Test
    void restoreAt_joinMessageLeaveJoin_correctFinalState() {
        saveEvent(EventType.JOIN, "user1", 1, BASE.plusMinutes(1));
        saveEvent(EventType.MESSAGE, "user1", 2, BASE.plusMinutes(2), Map.of("content", "hello"));
        saveEvent(EventType.LEAVE, "user1", 3, BASE.plusMinutes(3));
        saveEvent(EventType.JOIN, "user1", 4, BASE.plusMinutes(4));

        TimelineResult result = service.restoreAt(new RestoreTimelineQuery(sessionId.value(), BASE.plusMinutes(5)));

        assertThat(result.state().getParticipants()).containsExactly("user1");
        assertThat(result.state().getMessages()).hasSize(1);
        assertThat(result.restoredFrom()).isEqualTo("FULL_REPLAY");
    }

    // Boundary case: at이 이벤트 serverReceivedAt과 정확히 일치하면 포함
    @Test
    void restoreAt_atExactlyMatchesEventTime_includesEvent() {
        LocalDateTime exactTime = BASE.plusMinutes(10);
        saveEvent(EventType.JOIN, "user1", 1, exactTime);

        TimelineResult result = service.restoreAt(new RestoreTimelineQuery(sessionId.value(), exactTime));

        assertThat(result.state().getParticipants()).containsExactly("user1");
    }

    // Invariant case: Snapshot+Delta 결과 == Full Replay 결과
    @Test
    void restoreAt_snapshotPlusDelta_equalsFullReplay() {
        saveEvent(EventType.JOIN, "user1", 1, BASE.plusMinutes(1));
        saveEvent(EventType.MESSAGE, "user1", 2, BASE.plusMinutes(2), Map.of("content", "hello"));
        saveEvent(EventType.JOIN, "user2", 3, BASE.plusMinutes(3));

        // 3개 이벤트 기반 Snapshot 생성
        Snapshot snapshot = service.createSnapshot(sessionId);
        snapshotRepository.save(snapshot);

        // Snapshot 이후 이벤트 추가
        saveEvent(EventType.MESSAGE, "user2", 4, BASE.plusMinutes(4), Map.of("content", "world"));

        LocalDateTime at = BASE.plusMinutes(5);

        // Full Replay (Snapshot 없을 때와 동일한 이벤트로 직접 계산)
        FakeSnapshotRepository emptySnapshotRepo = new FakeSnapshotRepository();
        EventProjectorRegistry registry = new EventProjectorRegistry(List.of(
                new JoinProjector(), new LeaveProjector(), new MessageProjector(),
                new DisconnectProjector(), new ReconnectProjector()
        ));
        SnapshotApplicationService fullReplayService = new SnapshotApplicationService(
                chatEventRepository, emptySnapshotRepo, sessionRepository, registry);
        TimelineResult fullReplay = fullReplayService.restoreAt(new RestoreTimelineQuery(sessionId.value(), at));

        // Snapshot+Delta
        TimelineResult snapshotPlusDelta = service.restoreAt(new RestoreTimelineQuery(sessionId.value(), at));

        assertThat(snapshotPlusDelta.state().getParticipants())
                .isEqualTo(fullReplay.state().getParticipants());
        assertThat(snapshotPlusDelta.state().getMessages().size())
                .isEqualTo(fullReplay.state().getMessages().size());
        assertThat(snapshotPlusDelta.restoredFrom()).isEqualTo("SNAPSHOT_PLUS_REPLAY");
        assertThat(fullReplay.restoredFrom()).isEqualTo("FULL_REPLAY");
    }

    private void saveEvent(EventType type, String userId, long sequenceNo, LocalDateTime receivedAt) {
        saveEvent(type, userId, sequenceNo, receivedAt, Map.of());
    }

    private void saveEvent(EventType type, String userId, long sequenceNo,
                           LocalDateTime receivedAt, Map<String, Object> payload) {
        chatEventRepository.save(new ChatEvent(
                ChatEventId.create(),
                sessionId,
                userId,
                ClientEventId.of(UUID.randomUUID().toString()),
                type,
                payload,
                sequenceNo,
                receivedAt
        ));
    }
}
