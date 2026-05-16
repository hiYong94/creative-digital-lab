package com.creatived.chat.domain.session;

public class InvalidSessionStateException extends RuntimeException {

    public InvalidSessionStateException(String message) {
        super(message);
    }
}
