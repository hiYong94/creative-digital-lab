package com.creatived.chat.domain.snapshot.projector;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.snapshot.EventProjector;
import com.creatived.chat.domain.snapshot.MessageView;
import com.creatived.chat.domain.snapshot.SessionState;

public class MessageProjector implements EventProjector {

    @Override
    public EventType supportedType() {
        return EventType.MESSAGE;
    }

    @Override
    public SessionState apply(SessionState state, ChatEvent event) {
        String content = (String) event.getPayload().getOrDefault("content", "");
        MessageView message = new MessageView(
                event.getId().toString(),
                event.getUserId(),
                content,
                event.getServerReceivedAt()
        );
        return state
                .withMessageAdded(message)
                .withLastSequenceNo(event.getSequenceNo());
    }
}
