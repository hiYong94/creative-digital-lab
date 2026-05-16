package com.creatived.chat.infrastructure.persistence.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SnapshotJpaRepository extends JpaRepository<SnapshotJpaEntity, UUID> {

    Optional<SnapshotJpaEntity> findTopBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
