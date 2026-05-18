package com.creatived.chat.domain.session;

public class InvalidSessionStateException extends com.creatived.chat.domain.DomainException {

    public InvalidSessionStateException(String message) {
        super(message);
    }
}
