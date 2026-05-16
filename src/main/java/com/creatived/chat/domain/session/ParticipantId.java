package com.creatived.chat.domain.session;

import java.util.UUID;

public record ParticipantId(UUID value) {

    public static ParticipantId create() {
        return new ParticipantId(UUID.randomUUID());
    }

    public static ParticipantId of(UUID value) {
        return new ParticipantId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
