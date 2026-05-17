package com.creatived.chat.infrastructure.persistence.snapshot;

import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.snapshot.MessageView;
import com.creatived.chat.domain.snapshot.SessionState;
import com.creatived.chat.domain.snapshot.Snapshot;
import com.creatived.chat.infrastructure.persistence.converter.JsonMapConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SnapshotJpaEntity {

    @Comment("Snapshot 고유 식별자 (BINARY 16)")
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @Comment("소속 세션 식별자")
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID sessionId;

    @Comment("Snapshot 생성 시점의 마지막 이벤트 식별자")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID lastEventId;

    @Comment("Snapshot 시점의 세션 상태 (participants, messages) JSON 직렬화")
    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private Map<String, Object> state;

    @Comment("Snapshot 생성 시점의 마지막 sequenceNo. Delta Replay 시작점으로 사용")
    @Column(nullable = false)
    private long lastSequenceNo;

    @Comment("Snapshot 생성 시각")
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static SnapshotJpaEntity from(Snapshot snapshot) {
        SnapshotJpaEntity entity = new SnapshotJpaEntity();
        entity.id = snapshot.getId();
        entity.sessionId = snapshot.getSessionId().value();
        entity.lastEventId = snapshot.getLastEventId() != null ? snapshot.getLastEventId().value() : null;
        entity.state = serializeState(snapshot.getState());
        entity.lastSequenceNo = snapshot.getLastSequenceNo();
        entity.createdAt = snapshot.getCreatedAt();
        return entity;
    }

    public Snapshot toDomain() {
        return new Snapshot(
                id,
                SessionId.of(sessionId),
                lastEventId != null ? ChatEventId.of(lastEventId) : null,
                deserializeState(state),
                lastSequenceNo,
                createdAt
        );
    }

    private static Map<String, Object> serializeState(SessionState state) {
        return Map.of(
                "participants", state.getParticipants().stream().toList(),
                "messages", state.getMessages().stream()
                        .map(m -> Map.of(
                                "eventId", m.eventId(),
                                "userId", m.userId(),
                                "content", m.content(),
                                "sentAt", m.sentAt().toString()
                        )).toList(),
                "lastSequenceNo", state.getLastSequenceNo()
        );
    }

    @SuppressWarnings("unchecked")
    private static SessionState deserializeState(Map<String, Object> raw) {
        List<String> participants = (List<String>) raw.getOrDefault("participants", List.of());
        List<Map<String, Object>> messages = (List<Map<String, Object>>) raw.getOrDefault("messages", List.of());

        SessionState state = SessionState.empty();
        for (String userId : participants) {
            state = state.withParticipantAdded(userId);
        }
        for (Map<String, Object> m : messages) {
            state = state.withMessageAdded(new MessageView(
                    (String) m.get("eventId"),
                    (String) m.get("userId"),
                    (String) m.get("content"),
                    LocalDateTime.parse((String) m.get("sentAt"))
            ));
        }
        return state.withLastSequenceNo(((Number) raw.getOrDefault("lastSequenceNo", 0L)).longValue());
    }
}
