package com.creatived.chat.domain.snapshot.projector;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.snapshot.EventProjector;
import com.creatived.chat.domain.snapshot.SessionState;

public class LeaveProjector implements EventProjector {

    @Override
    public EventType supportedType() {
        return EventType.LEAVE;
    }

    @Override
    public SessionState apply(SessionState state, ChatEvent event) {
        return state
                .withParticipantRemoved(event.getUserId())
                .withLastSequenceNo(event.getSequenceNo());
    }
}
