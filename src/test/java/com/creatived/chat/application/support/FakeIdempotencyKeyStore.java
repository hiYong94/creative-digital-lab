package com.creatived.chat.application.support;

import java.util.HashSet;
import java.util.Set;

public class FakeIdempotencyKeyStore implements IdempotencyKeyStore {

    private final Set<String> keys = new HashSet<>();

    @Override
    public boolean exists(String clientEventId) {
        return keys.contains(clientEventId);
    }

    @Override
    public void store(String clientEventId) {
        keys.add(clientEventId);
    }
}
