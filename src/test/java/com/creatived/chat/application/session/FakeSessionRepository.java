package com.creatived.chat.application.session;

import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionRepository;
import com.creatived.chat.domain.session.SessionStatus;

import java.time.LocalDateTime;
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
    public List<Session> findAll(int page, int size, SessionStatus status, LocalDateTime from, LocalDateTime to) {
        List<Session> filtered = store.values().stream()
                .filter(s -> status == null || s.getStatus() == status)
                .filter(s -> from == null || !s.getCreatedAt().isBefore(from))
                .filter(s -> to == null || !s.getCreatedAt().isAfter(to))
                .toList();
        int start = page * size;
        if (start >= filtered.size()) return List.of();
        return new ArrayList<>(filtered).subList(start, Math.min(start + size, filtered.size()));
    }

    @Override
    public long count() {
        return store.size();
    }
}
