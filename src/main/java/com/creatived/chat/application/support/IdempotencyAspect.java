package com.creatived.chat.application.support;

import com.creatived.chat.application.event.CollectEventCommand;
import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventRepository;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.application.support.IdempotencyKeyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Aspect
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private final IdempotencyKeyStore keyStore;
    private final ChatEventRepository chatEventRepository;

    @Around("@annotation(com.creatived.chat.application.support.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint pjp) throws Throwable {
        CollectEventCommand command = extractCommand(pjp);
        if (command == null) {
            return pjp.proceed();
        }

        String clientEventId = command.clientEventId();

        try {
            if (keyStore.exists(clientEventId)) {
                Optional<ChatEvent> existing = chatEventRepository.findBySessionIdAndClientEventId(
                        SessionId.of(command.sessionId()), ClientEventId.of(clientEventId));
                if (existing.isPresent()) {
                    log.info("중복 이벤트 차단 (Redis): clientEventId={}", clientEventId);
                    return existing.get();
                }
            }
        } catch (Exception e) {
            log.warn("Redis 조회 실패, Idempotency 검사를 건너뜁니다. clientEventId={}", clientEventId, e);
        }

        Object result = pjp.proceed();

        try {
            keyStore.store(clientEventId);
        } catch (Exception e) {
            log.warn("Redis 저장 실패. clientEventId={}", clientEventId, e);
        }

        return result;
    }

    private CollectEventCommand extractCommand(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof CollectEventCommand cmd) {
                return cmd;
            }
        }
        return null;
    }
}
