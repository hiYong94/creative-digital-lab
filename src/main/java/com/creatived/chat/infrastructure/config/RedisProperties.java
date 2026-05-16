package com.creatived.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.redis")
public record RedisProperties(int idempotencyTtlMinutes, int presenceTtlSeconds) {
}
