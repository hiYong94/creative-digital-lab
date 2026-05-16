package com.creatived.chat.domain.snapshot;

import com.creatived.chat.domain.session.SessionId;

import java.util.Optional;

public interface SnapshotRepository {

    Snapshot save(Snapshot snapshot);

    Optional<Snapshot> findLatestBySessionId(SessionId sessionId);
}
