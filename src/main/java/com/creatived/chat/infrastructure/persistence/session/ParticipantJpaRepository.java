package com.creatived.chat.infrastructure.persistence.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ParticipantJpaRepository extends JpaRepository<ParticipantJpaEntity, UUID> {
}
