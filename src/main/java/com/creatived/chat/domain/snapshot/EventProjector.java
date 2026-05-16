package com.creatived.chat.domain.snapshot;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.EventType;

public interface EventProjector {

    EventType supportedType();

    SessionState apply(SessionState state, ChatEvent event);
}
