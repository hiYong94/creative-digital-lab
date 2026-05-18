package com.creatived.chat.application.event;

import com.creatived.chat.domain.event.ChatEvent;

public class EventCollectedEvent {

    private final ChatEvent chatEvent;

    public EventCollectedEvent(ChatEvent chatEvent) {
        this.chatEvent = chatEvent;
    }

    public ChatEvent getChatEvent() {
        return chatEvent;
    }
}
