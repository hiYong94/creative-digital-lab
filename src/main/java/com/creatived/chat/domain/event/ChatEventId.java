package com.creatived.chat.domain.event;

import java.util.UUID;

public record ChatEventId(UUID value) {

    public static ChatEventId create() {
        return new ChatEventId(UUID.randomUUID());
    }

    public static ChatEventId of(UUID value) {
        return new ChatEventId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
