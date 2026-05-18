package com.creatived.chat.application.support;

import com.creatived.chat.application.event.CollectEventCommand;
import com.creatived.chat.application.snapshot.FakeChatEventRepository;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.session.SessionId;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyAspectTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    private FakeIdempotencyKeyStore keyStore;
    private FakeChatEventRepository chatEventRepository;
    private IdempotencyAspect aspect;
    private SessionId sessionId;

    @BeforeEach
    void setUp() {
        keyStore = new FakeIdempotencyKeyStore();
        chatEventRepository = new FakeChatEventRepository();
        aspect = new IdempotencyAspect(keyStore, chatEventRepository);
        sessionId = SessionId.create();
    }

    // Mirror case: Redis 키 없음 → proceed() 호출 후 키 저장
    @Test
    void firstCall_noRedisKey_proceedsAndStoresKey() throws Throwable {
        String clientEventId = UUID.randomUUID().toString();
        CollectEventCommand command = makeCommand(clientEventId);
        ChatEvent event = makeEvent(clientEventId);
        ProceedingJoinPoint pjp = mockJoinPoint(command, event);

        Object result = aspect.checkIdempotency(pjp);

        assertThat(result).isEqualTo(event);
        assertThat(keyStore.exists(clientEventId)).isTrue();
        verify(pjp).proceed();
    }

    // Idempotency case: Redis 키 존재 → proceed() 미호출, 기존 이벤트 반환
    @Test
    void secondCall_redisKeyExists_returnsCachedEventWithoutProceeding() throws Throwable {
        String clientEventId = UUID.randomUUID().toString();
        CollectEventCommand command = makeCommand(clientEventId);
        ChatEvent event = makeEvent(clientEventId);
        chatEventRepository.save(event);
        keyStore.store(clientEventId);

        ProceedingJoinPoint pjp = mockJoinPoint(command, null);

        ChatEvent result = (ChatEvent) aspect.checkIdempotency(pjp);

        assertThat(result.getClientEventId().value()).isEqualTo(clientEventId);
        verify(pjp, never()).proceed();
    }

    // Idempotency case: 동일 clientEventId 3회 호출 → proceed()는 1회만 실행
    @Test
    void threeCallsSameClientEventId_proceedsOnlyOnce() throws Throwable {
        String clientEventId = UUID.randomUUID().toString();
        CollectEventCommand command = makeCommand(clientEventId);
        ChatEvent event = makeEvent(clientEventId);

        ProceedingJoinPoint first = mockJoinPoint(command, event);
        aspect.checkIdempotency(first);
        chatEventRepository.save(event);

        ProceedingJoinPoint second = mockJoinPoint(command, null);
        aspect.checkIdempotency(second);

        ProceedingJoinPoint third = mockJoinPoint(command, null);
        aspect.checkIdempotency(third);

        verify(first).proceed();
        verify(second, never()).proceed();
        verify(third, never()).proceed();
    }

    private CollectEventCommand makeCommand(String clientEventId) {
        return new CollectEventCommand(
                sessionId.value(), "user1", clientEventId,
                EventType.MESSAGE, Map.of(), 1L
        );
    }

    private ChatEvent makeEvent(String clientEventId) {
        return new ChatEvent(
                ChatEventId.create(), sessionId, "user1",
                ClientEventId.of(clientEventId), EventType.MESSAGE,
                Map.of(), 1L, NOW
        );
    }

    private ProceedingJoinPoint mockJoinPoint(CollectEventCommand command, ChatEvent returnValue) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{command});
        when(pjp.proceed()).thenReturn(returnValue);
        return pjp;
    }
}
