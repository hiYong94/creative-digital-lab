package com.creatived.chat.application.support;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Slf4j
public class UseCaseLoggingAspect {

    @Around("@annotation(useCase)")
    public Object logUseCase(ProceedingJoinPoint pjp, UseCase useCase) throws Throwable {
        String name = useCase.value().isBlank() ? pjp.getSignature().getName() : useCase.value();

        MDC.put("traceId", UUID.randomUUID().toString());
        MDC.put("useCase", name);
        enrichMdc(pjp.getArgs());

        long start = System.currentTimeMillis();
        try {
            log.info("유즈케이스 시작: {}", name);
            Object result = pjp.proceed();
            log.info("유즈케이스 완료: {} ({}ms)", name, elapsed(start));
            return result;
        } catch (Exception e) {
            log.error("유즈케이스 실패: {} ({}ms)", name, elapsed(start), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    // 도메인 커맨드 타입별 MDC 보강 — 각 페이즈에서 커맨드 클래스가 추가될 때 확장
    protected void enrichMdc(Object[] args) {
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
