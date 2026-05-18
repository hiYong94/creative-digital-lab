package com.creatived.chat.domain.event;

public class UnsupportedEventTypeException extends com.creatived.chat.domain.DomainException {

    public UnsupportedEventTypeException(EventType type) {
        super("지원하지 않는 이벤트 타입입니다. type=" + type);
    }
}
