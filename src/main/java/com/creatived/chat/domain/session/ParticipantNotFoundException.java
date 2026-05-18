package com.creatived.chat.domain.session;

public class ParticipantNotFoundException extends com.creatived.chat.domain.DomainException {

    public ParticipantNotFoundException(String userId, SessionId sessionId) {
        super("활성 참여자를 찾을 수 없습니다. userId=" + userId + ", sessionId=" + sessionId.value());
    }
}
