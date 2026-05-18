package com.creatived.chat.application.session;

import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FakeSessionRepository implements SessionRepository {

    private final Map<SessionId, Session> store = new LinkedHashMap<>();

    @Override
    public Session save(Session session) {
        store.put(session.getId(), session);
        return session;
    }

    @Override
    public Optional<Session> findById(SessionId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Session> findAll(int page, int size) {
        List<Session> all = new ArrayList<>(store.values());
        int from = page * size;
        if (from >= all.size()) return List.of();
        return all.subList(from, Math.min(from + size, all.size()));
    }

    @Override
    public long count() {
        return store.size();
    }
}
