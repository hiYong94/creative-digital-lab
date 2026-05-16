package com.creatived.chat.infrastructure.persistence.snapshot;

import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.snapshot.Snapshot;
import com.creatived.chat.domain.snapshot.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SnapshotRepositoryAdapter implements SnapshotRepository {

    private final SnapshotJpaRepository snapshotJpaRepository;

    @Override
    public Snapshot save(Snapshot snapshot) {
        return snapshotJpaRepository.save(SnapshotJpaEntity.from(snapshot)).toDomain();
    }

    @Override
    public Optional<Snapshot> findLatestBySessionId(SessionId sessionId) {
        return snapshotJpaRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId.value())
                .map(SnapshotJpaEntity::toDomain);
    }
}
