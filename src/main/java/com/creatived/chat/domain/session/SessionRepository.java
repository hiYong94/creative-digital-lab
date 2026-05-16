package com.creatived.chat.domain.session;

import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(SessionId id);

    List<Session> findAll(int page, int size);

    long count();
}
