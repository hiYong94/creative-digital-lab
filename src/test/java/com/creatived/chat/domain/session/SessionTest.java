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

    // Idempotency: ENDED 세션 재종료 → 동일 상태 유지, 예외 없음
    @Test
    void end_endedSession_isIdempotent() {
        Session session = newSession();
        session.end(NOW);

        session.end(NOW.plusMinutes(1));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(session.getEndedAt()).isEqualTo(NOW);
    }

    // State transition: ACTIVE 세션 참여 성공
    @Test
    void join_activeSession_addsParticipant() {
        Session session = newSession();

        session.join("user2", NOW);

        assertThat(session.getParticipants()).hasSize(2);
    }

    // Mirror: ENDED 세션 참여 → 예외
    @Test
    void join_endedSession_throwsInvalidSessionStateException() {
        Session session = newSession();
        session.end(NOW);

        assertThatThrownBy(() -> session.join("user2", NOW))
                .isInstanceOf(InvalidSessionStateException.class);
    }

    // Invariant: 동일 userId 중복 참여 → 예외
    @Test
    void join_duplicateUserId_throwsAlreadyJoinedException() {
        Session session = newSession();

        assertThatThrownBy(() -> session.join("user1", NOW))
                .isInstanceOf(AlreadyJoinedException.class);
    }

    // Sequence: 퇴장 후 재참여 허용
    @Test
    void join_afterLeave_allowsRejoin() {
        Session session = newSession();
        session.join("user2", NOW);
        session.leave("user1", NOW.plusMinutes(1));

        session.join("user1", NOW.plusMinutes(2));

        long activeCount = session.getParticipants().stream().filter(p -> !p.hasLeft()).count();
        assertThat(activeCount).isEqualTo(2);
    }

    // Sequence: 마지막 참여자 퇴장 시 세션 자동 종료
    @Test
    void leave_lastParticipant_autoEndsSession() {
        Session session = newSession();
        session.join("user2", NOW);

        session.leave("user1", NOW.plusMinutes(1));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        session.leave("user2", NOW.plusMinutes(2));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
    }

    // Mirror case: 3번째 참여자 시도 → SessionCapacityExceededException (1:1 정원 초과)
    @Test
    void join_thirdParticipant_throwsSessionCapacityExceededException() {
        Session session = newSession();
        session.join("user2", NOW);

        assertThatThrownBy(() -> session.join("user3", NOW))
                .isInstanceOf(SessionCapacityExceededException.class);
    }

    // State transition: ENDED 세션 퇴장 시도 → 예외
    @Test
    void leave_endedSession_throwsInvalidSessionStateException() {
        Session session = newSession();
        session.end(NOW);

        assertThatThrownBy(() -> session.leave("user1", NOW.plusMinutes(1)))
                .isInstanceOf(InvalidSessionStateException.class);
    }

    private Session newSession() {
        Session session = new Session(SessionId.create(), NOW);
        session.join("user1", NOW);
        return session;
    }
}
