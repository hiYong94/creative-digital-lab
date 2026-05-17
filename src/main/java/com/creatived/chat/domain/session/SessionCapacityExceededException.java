package com.creatived.chat.domain.session;

public class SessionCapacityExceededException extends RuntimeException {

    public SessionCapacityExceededException(SessionId sessionId) {
        super("세션 정원이 초과되었습니다. id=" + sessionId.value());
    }
}
