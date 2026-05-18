package com.creatived.chat.infrastructure.redis;

import com.creatived.chat.application.support.IdempotencyKeyStore;
import com.creatived.chat.infrastructure.config.RedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisIdempotencyKeyStore implements IdempotencyKeyStore {

    private final StringRedisTemplate redisTemplate;
    private final RedisProperties redisProperties;

    @Override
    public boolean exists(String clientEventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(clientEventId)));
    }

    @Override
    public void store(String clientEventId) {
        redisTemplate.opsForValue().set(
                key(clientEventId), "1",
                redisProperties.idempotencyTtlMinutes(), TimeUnit.MINUTES
        );
    }

    private String key(String clientEventId) {
        return "idempotency:" + clientEventId;
    }
}
