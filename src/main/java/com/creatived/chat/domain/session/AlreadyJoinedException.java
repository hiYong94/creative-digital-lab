package com.creatived.chat.domain.session;

public class AlreadyJoinedException extends com.creatived.chat.domain.DomainException {

    public AlreadyJoinedException(String userId) {
        super("이미 세션에 참여 중입니다. userId=" + userId);
    }
}
