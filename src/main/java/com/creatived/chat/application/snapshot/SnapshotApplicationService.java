package com.creatived.chat.application.snapshot;

import com.creatived.chat.application.support.UseCase;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventRepository;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionNotFoundException;
import com.creatived.chat.domain.session.SessionRepository;
import com.creatived.chat.domain.snapshot.EventProjectorRegistry;
import com.creatived.chat.domain.snapshot.SessionState;
import com.creatived.chat.domain.snapshot.Snapshot;
import com.creatived.chat.domain.snapshot.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotApplicationService {

    private final ChatEventRepository chatEventRepository;
    private final SnapshotRepository snapshotRepository;
    private final SessionRepository sessionRepository;
    private final EventProjectorRegistry projectorRegistry;

    @UseCase("타임라인 복원")
    @Transactional(readOnly = true)
    public TimelineResult restoreAt(RestoreTimelineQuery query) {
        SessionId sessionId = SessionId.of(query.sessionId());

        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        List<ChatEvent> applicableEvents = chatEventRepository
                .findBySessionIdAndServerReceivedAtBeforeOrEqual(sessionId, query.at());

        Optional<Snapshot> snapshotOpt = snapshotRepository.findLatestBySessionId(sessionId);

        if (snapshotOpt.isPresent()) {
            Snapshot snapshot = snapshotOpt.get();
            long maxApplicableSeqNo = applicableEvents.stream()
                    .mapToLong(ChatEvent::getSequenceNo).max().orElse(-1L);

            if (snapshot.getLastSequenceNo() <= maxApplicableSeqNo) {
                List<ChatEvent> deltaEvents = applicableEvents.stream()
                        .filter(e -> e.getSequenceNo() > snapshot.getLastSequenceNo())
                        .toList();
                SessionState state = applyEvents(snapshot.getState(), deltaEvents);
                return new TimelineResult(state, "SNAPSHOT_PLUS_REPLAY");
            }
        }

        SessionState state = applyEvents(SessionState.empty(), applicableEvents);
        return new TimelineResult(state, "FULL_REPLAY");
    }

    @UseCase("Snapshot 생성")
    @Transactional
    public Snapshot createSnapshot(SessionId sessionId) {
        List<ChatEvent> allEvents = chatEventRepository
                .findBySessionIdOrderByServerReceivedAtAscSequenceNoAsc(sessionId);

        SessionState state = applyEvents(SessionState.empty(), allEvents);

        ChatEvent lastEvent = allEvents.isEmpty() ? null : allEvents.get(allEvents.size() - 1);

        Snapshot snapshot = new Snapshot(
                UUID.randomUUID(),
                sessionId,
                lastEvent != null ? lastEvent.getId() : null,
                state,
                state.getLastSequenceNo(),
                LocalDateTime.now()
        );

        return snapshotRepository.save(snapshot);
    }

    private SessionState applyEvents(SessionState initial, List<ChatEvent> events) {
        SessionState state = initial;
        Set<Long> appliedSequenceNos = new HashSet<>();

        for (ChatEvent event : events) {
            if (appliedSequenceNos.contains(event.getSequenceNo())) {
                log.warn("중복 sequenceNo 건너뜀: sequenceNo={}, clientEventId={}",
                        event.getSequenceNo(), event.getClientEventId());
                continue;
            }
            state = projectorRegistry.get(event.getType()).apply(state, event);
            appliedSequenceNos.add(event.getSequenceNo());
        }

        return state;
    }
}
