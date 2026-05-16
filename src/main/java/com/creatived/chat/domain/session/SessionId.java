package com.creatived.chat.domain.session;

import java.util.UUID;

public record SessionId(UUID value) {

    public static SessionId create() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId of(UUID value) {
        return new SessionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
