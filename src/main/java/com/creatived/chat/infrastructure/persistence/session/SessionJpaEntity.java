package com.creatived.chat.infrastructure.persistence.session;

import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.session.SessionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionJpaEntity {

    @Comment("세션 고유 식별자 (BINARY 16)")
    @Id
    private UUID id;

    @Comment("세션 상태 (ACTIVE | ENDED)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Comment("세션 생성 시각")
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Comment("세션 종료 시각. ACTIVE 상태일 때 null")
    private LocalDateTime endedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ParticipantJpaEntity> participants;

    public static SessionJpaEntity from(Session session) {
        SessionJpaEntity entity = new SessionJpaEntity();
        entity.id = session.getId().value();
        entity.status = session.getStatus();
        entity.createdAt = session.getCreatedAt();
        entity.endedAt = session.getEndedAt();
        return entity;
    }

    public void update(Session session) {
        this.status = session.getStatus();
        this.endedAt = session.getEndedAt();
    }

    public Session toDomain() {
        List<com.creatived.chat.domain.session.Participant> domainParticipants = participants == null
                ? List.of()
                : participants.stream().map(ParticipantJpaEntity::toDomain).toList();
        return new Session(
                SessionId.of(id),
                status,
                createdAt,
                endedAt,
                domainParticipants
        );
    }
}
