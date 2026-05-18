package com.creatived.chat.application.snapshot;

import com.creatived.chat.domain.snapshot.SessionState;

public record TimelineResult(SessionState state, String restoredFrom) {
}
