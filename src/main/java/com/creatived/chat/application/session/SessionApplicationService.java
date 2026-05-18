package com.creatived.chat.application.session;

import com.creatived.chat.application.support.UseCase;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.event.ChatEventRepository;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionNotFoundException;
import com.creatived.chat.domain.session.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionApplicationService {

    private final SessionRepository sessionRepository;
    private final ChatEventRepository chatEventRepository;

    @UseCase("세션 생성")
    @Transactional
    public Session create(CreateSessionCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Session session = new Session(SessionId.create(), now);
        session.join(command.userId(), now);
        Session saved = sessionRepository.save(session);
        saveJoinEvent(saved.getId(), command.userId(), now);
        return saved;
    }

    @UseCase("세션 참여")
    @Transactional
    public Session join(JoinSessionCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Session session = findOrThrow(SessionId.of(command.sessionId()));
        session.join(command.userId(), now);
        Session saved = sessionRepository.save(session);
        saveJoinEvent(saved.getId(), command.userId(), now);
        return saved;
    }

    @UseCase("세션 종료")
    @Transactional
    public Session end(EndSessionCommand command) {
        Session session = findOrThrow(SessionId.of(command.sessionId()));
        session.end(LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @UseCase("세션 단건 조회")
    @Transactional(readOnly = true)
    public Session getById(GetSessionQuery query) {
        return findOrThrow(SessionId.of(query.sessionId()));
    }

    @UseCase("세션 목록 조회")
    @Transactional(readOnly = true)
    public List<Session> getList(GetSessionListQuery query) {
        return sessionRepository.findAll(query.page(), query.size(), query.status(), query.from(), query.to());
    }

    private Session findOrThrow(SessionId id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    private void saveJoinEvent(SessionId sessionId, String userId, LocalDateTime now) {
        long nextSeq = chatEventRepository.countBySessionId(sessionId) + 1;
        chatEventRepository.save(new ChatEvent(
                ChatEventId.create(),
                sessionId,
                userId,
                ClientEventId.of(UUID.randomUUID().toString()),
                EventType.JOIN,
                Map.of(),
                nextSeq,
                now
        ));
    }
}
