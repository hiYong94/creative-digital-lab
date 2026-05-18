package com.creatived.chat.infrastructure.redis;

import com.creatived.chat.infrastructure.config.RedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PresenceStore {

    private final StringRedisTemplate redisTemplate;
    private final RedisProperties redisProperties;

    public void heartbeat(String sessionId, String userId) {
        redisTemplate.opsForValue().set(
                key(sessionId, userId), "1",
                redisProperties.presenceTtlSeconds(), TimeUnit.SECONDS
        );
    }

    public boolean isOnline(String sessionId, String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(sessionId, userId)));
    }

    public void remove(String sessionId, String userId) {
        redisTemplate.delete(key(sessionId, userId));
    }

    private String key(String sessionId, String userId) {
        return "session:" + sessionId + ":presence:" + userId;
    }
}
