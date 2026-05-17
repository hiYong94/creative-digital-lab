package com.creatived.chat.infrastructure.persistence.session;

import com.creatived.chat.domain.session.Participant;
import com.creatived.chat.domain.session.ParticipantId;
import com.creatived.chat.domain.session.SessionId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipantJpaEntity {

    @Comment("참여자 고유 식별자 (BINARY 16)")
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @Comment("소속 세션")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionJpaEntity session;

    @Comment("참여자 식별자. 인증 없이 클라이언트가 제공하는 문자열")
    @Column(nullable = false, length = 64)
    private String userId;

    @Comment("세션 참여 시각")
    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Comment("세션 퇴장 시각. 참여 중일 때 null")
    private LocalDateTime leftAt;

    public static ParticipantJpaEntity from(Participant participant, SessionJpaEntity sessionEntity) {
        ParticipantJpaEntity entity = new ParticipantJpaEntity();
        entity.id = participant.getId().value();
        entity.session = sessionEntity;
        entity.userId = participant.getUserId();
        entity.joinedAt = participant.getJoinedAt();
        entity.leftAt = participant.getLeftAt();
        return entity;
    }

    public void leave(LocalDateTime leftAt) {
        this.leftAt = leftAt;
    }

    public Participant toDomain() {
        Participant participant = new Participant(
                ParticipantId.of(id),
                SessionId.of(session.getId()),
                userId,
                joinedAt
        );
        if (leftAt != null) {
            participant.leave(leftAt);
        }
        return participant;
    }
}
