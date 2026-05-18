package com.creatived.chat.application.event;

import com.creatived.chat.application.support.Idempotent;
import com.creatived.chat.application.support.UseCase;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.event.ChatEventRepository;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionNotFoundException;
import com.creatived.chat.domain.session.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventApplicationService {

    private final ChatEventRepository chatEventRepository;
    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @UseCase("이벤트 수집")
    @Idempotent
    @Transactional
    public ChatEvent collect(CollectEventCommand command) {
        SessionId sessionId = SessionId.of(command.sessionId());

        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId))
                .requireActive();

        ChatEventId newId = ChatEventId.create();
        ChatEvent event = new ChatEvent(
                newId,
                sessionId,
                command.userId(),
                ClientEventId.of(command.clientEventId()),
                command.type(),
                command.payload() != null ? command.payload() : java.util.Map.of(),
                command.sequenceNo(),
                LocalDateTime.now()
        );

        ChatEvent saved = chatEventRepository.save(event);

        // INSERT IGNORE가 중복을 차단한 경우 id가 다르므로 이벤트를 발행하지 않음
        if (saved.getId().equals(newId)) {
            eventPublisher.publishEvent(new EventCollectedEvent(saved));
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public long nextSequenceNo(UUID sessionId) {
        return chatEventRepository.countBySessionId(SessionId.of(sessionId)) + 1;
    }

    @UseCase("누락 이벤트 조회")
    @Transactional(readOnly = true)
    public List<ChatEvent> findMissed(UUID sessionId, long resumeFromSequenceNo) {
        return chatEventRepository.findBySessionIdAndSequenceNoAfter(
                SessionId.of(sessionId), resumeFromSequenceNo);
    }

    @UseCase("이벤트 기간 조회")
    @Transactional(readOnly = true)
    public List<ChatEvent> findByPeriod(GetEventsByPeriodQuery query) {
        SessionId sessionId = SessionId.of(query.sessionId());
        List<ChatEvent> events = chatEventRepository
                .findBySessionIdAndServerReceivedAtBeforeOrEqual(sessionId, query.to());

        if (query.from() != null) {
            return events.stream()
                    .filter(e -> !e.getServerReceivedAt().isBefore(query.from()))
                    .toList();
        }
        return events;
    }
}
