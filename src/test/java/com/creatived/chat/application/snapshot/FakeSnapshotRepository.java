package com.creatived.chat.application.snapshot;

import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.snapshot.Snapshot;
import com.creatived.chat.domain.snapshot.SnapshotRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FakeSnapshotRepository implements SnapshotRepository {

    private final List<Snapshot> store = new ArrayList<>();

    @Override
    public Snapshot save(Snapshot snapshot) {
        store.add(snapshot);
        return snapshot;
    }

    @Override
    public Optional<Snapshot> findLatestBySessionId(SessionId sessionId) {
        return store.stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .max(Comparator.comparing(Snapshot::getCreatedAt));
    }
}
