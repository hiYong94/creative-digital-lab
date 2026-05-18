package com.creatived.chat.application.support;

public interface IdempotencyKeyStore {
    boolean exists(String clientEventId);
    void store(String clientEventId);
}
