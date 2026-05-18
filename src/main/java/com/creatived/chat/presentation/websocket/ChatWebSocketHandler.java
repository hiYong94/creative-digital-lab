package com.creatived.chat.presentation.websocket;

import com.creatived.chat.application.event.CollectEventCommand;
import com.creatived.chat.application.event.EventApplicationService;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.infrastructure.redis.PresenceStore;
import com.creatived.chat.presentation.websocket.dto.ChatEventResponse;
import com.creatived.chat.presentation.websocket.dto.ChatMessageRequest;
import com.creatived.chat.presentation.websocket.dto.HeartbeatRequest;
import com.creatived.chat.presentation.websocket.dto.ReconnectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler {

    private static final int MISSED_EVENT_LIMIT = 1000;
    private static final int MISSED_EVENT_FALLBACK_SIZE = 100;

    private final EventApplicationService eventApplicationService;
    private final PresenceStore presenceStore;
    private final SimpMessagingTemplate messagingTemplate;

    // STOMP 세션 ID → (chatSessionId, userId) 매핑. DISCONNECT 이벤트 자동 수집에 사용
    private final ConcurrentHashMap<String, ConnectedUser> registry = new ConcurrentHashMap<>();

    private record ConnectedUser(String chatSessionId, String userId) {}

    @MessageMapping("/sessions/{sessionId}/events")
    public void handleEvent(@DestinationVariable String sessionId,
                            @Payload ChatMessageRequest request,
                            SimpMessageHeaderAccessor headerAccessor) {
        registry.put(headerAccessor.getSessionId(), new ConnectedUser(sessionId, request.userId()));
        presenceStore.heartbeat(sessionId, request.userId());

        CollectEventCommand command = new CollectEventCommand(
                UUID.fromString(sessionId),
                request.userId(),
                request.clientEventId(),
                request.type(),
                request.payload() != null ? request.payload() : Map.of(),
                request.sequenceNo()
        );
        ChatEvent saved = eventApplicationService.collect(command);

        messagingTemplate.convertAndSend("/topic/sessions/" + sessionId, ChatEventResponse.from(saved));
    }

    @MessageMapping("/sessions/{sessionId}/reconnect")
    public void handleReconnect(@DestinationVariable String sessionId,
                                @Payload ReconnectRequest request,
                                SimpMessageHeaderAccessor headerAccessor) {
        String stompSessionId = headerAccessor.getSessionId();
        registry.put(stompSessionId, new ConnectedUser(sessionId, request.userId()));
        presenceStore.heartbeat(sessionId, request.userId());

        UUID sessionUuid = UUID.fromString(sessionId);
        CollectEventCommand reconnectCommand = new CollectEventCommand(
                sessionUuid,
                request.userId(),
                request.clientEventId(),
                EventType.RECONNECT,
                Map.of(),
                eventApplicationService.nextSequenceNo(sessionUuid)
        );
        eventApplicationService.collect(reconnectCommand);

        List<ChatEvent> missed = eventApplicationService.findMissed(
                UUID.fromString(sessionId), request.resumeFromSequenceNo());

        if (missed.size() > MISSED_EVENT_LIMIT) {
            log.warn("누락 이벤트 초과: sessionId={}, userId={}, count={}. 마지막 {}개만 전송.",
                    sessionId, request.userId(), missed.size(), MISSED_EVENT_FALLBACK_SIZE);
            missed = missed.subList(missed.size() - MISSED_EVENT_FALLBACK_SIZE, missed.size());
        }

        List<ChatEventResponse> responses = missed.stream().map(ChatEventResponse::from).toList();
        messagingTemplate.convertAndSendToUser(stompSessionId, "/queue/missed", responses);
    }

    @MessageMapping("/sessions/{sessionId}/heartbeat")
    public void handleHeartbeat(@DestinationVariable String sessionId,
                                @Payload HeartbeatRequest request,
                                SimpMessageHeaderAccessor headerAccessor) {
        registry.put(headerAccessor.getSessionId(), new ConnectedUser(sessionId, request.userId()));
        presenceStore.heartbeat(sessionId, request.userId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        ConnectedUser user = registry.remove(event.getSessionId());
        if (user == null) {
            return;
        }

        presenceStore.remove(user.chatSessionId(), user.userId());

        try {
            // DISCONNECT 이벤트는 서버가 생성. clientEventId/sequenceNo를 서버가 채번
            UUID chatSessionUuid = UUID.fromString(user.chatSessionId());
            CollectEventCommand command = new CollectEventCommand(
                    chatSessionUuid,
                    user.userId(),
                    UUID.randomUUID().toString(),
                    EventType.DISCONNECT,
                    Map.of(),
                    eventApplicationService.nextSequenceNo(chatSessionUuid)
            );
            eventApplicationService.collect(command);
        } catch (Exception e) {
            log.warn("DISCONNECT 이벤트 수집 실패: sessionId={}, userId={}",
                    user.chatSessionId(), user.userId(), e);
        }
    }
}
