package com.creatived.chat.infrastructure.persistence.session;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

    List<SessionJpaEntity> findAllBy(Pageable pageable);
}
