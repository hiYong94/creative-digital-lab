package com.creatived.chat.application.session;

import com.creatived.chat.application.support.UseCase;
import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionNotFoundException;
import com.creatived.chat.domain.session.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionApplicationService {

    private final SessionRepository sessionRepository;

    @UseCase("세션 생성")
    @Transactional
    public Session create(CreateSessionCommand command) {
        Session session = new Session(SessionId.create(), LocalDateTime.now());
        session.join(command.userId(), LocalDateTime.now());
        return sessionRepository.save(session);
    }

    @UseCase("세션 참여")
    @Transactional
    public Session join(JoinSessionCommand command) {
        Session session = findOrThrow(SessionId.of(command.sessionId()));
        session.join(command.userId(), LocalDateTime.now());
        return sessionRepository.save(session);
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
        return sessionRepository.findAll(query.page(), query.size());
    }

    private Session findOrThrow(SessionId id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new SessionNotFoundException(id));
    }
}
