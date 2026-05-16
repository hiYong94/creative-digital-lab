package com.creatived.chat.infrastructure.persistence.event;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.infrastructure.persistence.converter.JsonMapConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "chat_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatEventJpaEntity {

    @Comment("이벤트 고유 식별자 (BINARY 16)")
    @Id
    private UUID id;

    @Comment("소속 세션 식별자")
    @Column(nullable = false)
    private UUID sessionId;

    @Comment("이벤트 발생 참여자 식별자")
    @Column(nullable = false, length = 64)
    private String userId;

    @Comment("클라이언트 발급 이벤트 식별자. Idempotency 키로 사용")
    @Column(nullable = false, length = 64)
    private String clientEventId;

    @Comment("이벤트 타입 (JOIN | LEAVE | MESSAGE | DISCONNECT | RECONNECT)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType type;

    @Comment("이벤트 타입별 추가 데이터. 구조가 타입마다 다르므로 JSON으로 저장")
    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private Map<String, Object> payload;

    @Comment("세션 내 이벤트 순서. serverReceivedAt 동일 시각 tie-break에 사용")
    @Column(nullable = false)
    private long sequenceNo;

    @Comment("서버 수신 시각. 이벤트 정렬 1순위 기준")
    @Column(nullable = false)
    private LocalDateTime serverReceivedAt;

    public static ChatEventJpaEntity from(ChatEvent event) {
        ChatEventJpaEntity entity = new ChatEventJpaEntity();
        entity.id = event.getId().value();
        entity.sessionId = event.getSessionId().value();
        entity.userId = event.getUserId();
        entity.clientEventId = event.getClientEventId().value();
        entity.type = event.getType();
        entity.payload = event.getPayload();
        entity.sequenceNo = event.getSequenceNo();
        entity.serverReceivedAt = event.getServerReceivedAt();
        return entity;
    }

    public ChatEvent toDomain() {
        return new ChatEvent(
                ChatEventId.of(id),
                SessionId.of(sessionId),
                userId,
                ClientEventId.of(clientEventId),
                type,
                payload,
                sequenceNo,
                serverReceivedAt
        );
    }
}
