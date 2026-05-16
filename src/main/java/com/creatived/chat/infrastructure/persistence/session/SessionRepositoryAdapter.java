package com.creatived.chat.infrastructure.persistence.session;

import com.creatived.chat.domain.session.Participant;
import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SessionRepositoryAdapter implements SessionRepository {

    private final SessionJpaRepository sessionJpaRepository;
    private final ParticipantJpaRepository participantJpaRepository;

    @Override
    public Session save(Session session) {
        SessionJpaEntity sessionEntity = sessionJpaRepository.findById(session.getId().value())
                .map(entity -> { entity.update(session); return entity; })
                .orElseGet(() -> SessionJpaEntity.from(session));

        SessionJpaEntity saved = sessionJpaRepository.save(sessionEntity);
        syncParticipants(session.getParticipants(), saved);

        return sessionJpaRepository.findById(saved.getId())
                .map(SessionJpaEntity::toDomain)
                .orElseThrow();
    }

    @Override
    public Optional<Session> findById(SessionId id) {
        return sessionJpaRepository.findById(id.value())
                .map(SessionJpaEntity::toDomain);
    }

    @Override
    public List<Session> findAll(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return sessionJpaRepository.findAllBy(pageable).stream()
                .map(SessionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return sessionJpaRepository.count();
    }

    private void syncParticipants(List<Participant> participants, SessionJpaEntity sessionEntity) {
        Set<UUID> existingIds = participantJpaRepository.findAll().stream()
                .map(ParticipantJpaEntity::getId)
                .collect(Collectors.toSet());

        participants.forEach(participant -> {
            UUID participantId = participant.getId().value();
            if (!existingIds.contains(participantId)) {
                participantJpaRepository.save(ParticipantJpaEntity.from(participant, sessionEntity));
            } else if (participant.hasLeft()) {
                participantJpaRepository.findById(participantId).ifPresent(entity -> {
                    entity.leave(participant.getLeftAt());
                    participantJpaRepository.save(entity);
                });
            }
        });
    }
}
