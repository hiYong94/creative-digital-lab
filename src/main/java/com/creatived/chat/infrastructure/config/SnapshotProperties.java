package com.creatived.chat.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.snapshot")
public record SnapshotProperties(int interval, boolean asyncEnabled) {
}
