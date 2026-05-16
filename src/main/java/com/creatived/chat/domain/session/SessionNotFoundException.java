package com.creatived.chat.domain.session;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(SessionId id) {
        super("세션을 찾을 수 없습니다. id=" + id);
    }
}
