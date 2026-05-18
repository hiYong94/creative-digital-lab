package com.creatived.chat.infrastructure.persistence.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID>,
        JpaSpecificationExecutor<SessionJpaEntity> {
}
