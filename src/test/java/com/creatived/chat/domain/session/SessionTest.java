package com.creatived.chat.domain.session;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    // State transition: ACTIVE → ENDED 성공
    @Test
    void end_activeSession_changesStatusToEnded() {
        Session session = newSession();

        session.end(NOW);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(session.getEndedAt()).isEqualTo(NOW);
    }

    // Mirror: ENDED 세션 재종료 → 예외
    @Test
    void end_endedSession_throwsInvalidSessionStateException() {
        Session session = newSession();
        session.end(NOW);

        assertThatThrownBy(() -> session.end(NOW))
                .isInstanceOf(InvalidSessionStateException.class);
    }

    // State transition: ACTIVE 세션 참여 성공
    @Test
    void join_activeSession_addsParticipant() {
        Session session = newSession();

        Participant participant = session.join("user1", NOW);

        assertThat(participant.getUserId()).isEqualTo("user1");
        assertThat(session.getParticipants()).hasSize(1);
    }

    // Mirror: ENDED 세션 참여 → 예외
    @Test
    void join_endedSession_throwsInvalidSessionStateException() {
        Session session = newSession();
        session.end(NOW);

        assertThatThrownBy(() -> session.join("user1", NOW))
                .isInstanceOf(InvalidSessionStateException.class);
    }

    // Invariant: 동일 userId 중복 참여 → 예외
    @Test
    void join_duplicateUserId_throwsAlreadyJoinedException() {
        Session session = newSession();
        session.join("user1", NOW);

        assertThatThrownBy(() -> session.join("user1", NOW))
                .isInstanceOf(AlreadyJoinedException.class);
    }

    // Sequence: 퇴장 후 재참여 허용
    @Test
    void join_afterLeave_allowsRejoin() {
        Session session = newSession();
        Participant first = session.join("user1", NOW);
        first.leave(NOW.plusMinutes(1));

        Participant second = session.join("user1", NOW.plusMinutes(2));

        assertThat(second.getUserId()).isEqualTo("user1");
        assertThat(session.getParticipants()).hasSize(2);
    }

    private Session newSession() {
        return new Session(SessionId.create(), NOW);
    }
}
