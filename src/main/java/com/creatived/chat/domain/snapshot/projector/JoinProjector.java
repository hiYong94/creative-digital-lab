package com.creatived.chat.domain.snapshot.projector;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.snapshot.EventProjector;
import com.creatived.chat.domain.snapshot.SessionState;

public class JoinProjector implements EventProjector {

    @Override
    public EventType supportedType() {
        return EventType.JOIN;
    }

    @Override
    public SessionState apply(SessionState state, ChatEvent event) {
        return state
                .withParticipantAdded(event.getUserId())
                .withLastSequenceNo(event.getSequenceNo());
    }
}
