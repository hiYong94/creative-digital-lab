package com.creatived.chat.domain.event;

public record ClientEventId(String value) {

    public static ClientEventId of(String value) {
        return new ClientEventId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
