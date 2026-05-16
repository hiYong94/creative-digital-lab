package com.creatived.chat.domain.snapshot;

import com.creatived.chat.domain.event.ChatEvent;
import com.creatived.chat.domain.event.ChatEventId;
import com.creatived.chat.domain.event.ClientEventId;
import com.creatived.chat.domain.event.EventType;
import com.creatived.chat.domain.session.SessionId;
import com.creatived.chat.domain.snapshot.projector.DisconnectProjector;
import com.creatived.chat.domain.snapshot.projector.JoinProjector;
import com.creatived.chat.domain.snapshot.projector.LeaveProjector;
import com.creatived.chat.domain.snapshot.projector.MessageProjector;
import com.creatived.chat.domain.snapshot.projector.ReconnectProjector;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventProjectorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final SessionId SESSION_ID = SessionId.create();

    // JOIN: 참여자 추가
    @Test
    void joinProjector_addsParticipant() {
        SessionState state = new JoinProjector().apply(SessionState.empty(), event("user1", EventType.JOIN, Map.of(), 1));

        assertThat(state.getParticipants()).containsExactly("user1");
        assertThat(state.getLastSequenceNo()).isEqualTo(1);
    }

    // Invariant: JOIN 중복 적용 → 참여자 중복 없음
    @Test
    void joinProjector_duplicateApply_doesNotDuplicateParticipant() {
        JoinProjector projector = new JoinProjector();
        ChatEvent join = event("user1", EventType.JOIN, Map.of(), 1);

        SessionState state = projector.apply(projector.apply(SessionState.empty(), join), join);

        assertThat(state.getParticipants()).hasSize(1);
    }

    // LEAVE: 참여자 제거
    @Test
    void leaveProjector_removesParticipant() {
        SessionState withUser = new JoinProjector().apply(SessionState.empty(), event("user1", EventType.JOIN, Map.of(), 1));

        SessionState state = new LeaveProjector().apply(withUser, event("user1", EventType.LEAVE, Map.of(), 2));

        assertThat(state.getParticipants()).isEmpty();
        assertThat(state.getLastSequenceNo()).isEqualTo(2);
    }

    // Empty: 존재하지 않는 참여자 LEAVE → 예외 없이 무시
    @Test
    void leaveProjector_nonExistentUser_doesNotThrow() {
        SessionState state = new LeaveProjector().apply(SessionState.empty(), event("user1", EventType.LEAVE, Map.of(), 1));

        assertThat(state.getParticipants()).isEmpty();
    }

    // MESSAGE: 메시지 추가
    @Test
    void messageProjector_addsMessage() {
        SessionState state = new MessageProjector().apply(
                SessionState.empty(),
                event("user1", EventType.MESSAGE, Map.of("content", "hello"), 1)
        );

        assertThat(state.getMessages()).hasSize(1);
        assertThat(state.getMessages().get(0).content()).isEqualTo("hello");
        assertThat(state.getMessages().get(0).userId()).isEqualTo("user1");
    }

    // DISCONNECT: 참여자 오프라인 처리 (제거)
    @Test
    void disconnectProjector_removesParticipant() {
        SessionState withUser = new JoinProjector().apply(SessionState.empty(), event("user1", EventType.JOIN, Map.of(), 1));

        SessionState state = new DisconnectProjector().apply(withUser, event("user1", EventType.DISCONNECT, Map.of(), 2));

        assertThat(state.getParticipants()).isEmpty();
    }

    // RECONNECT: 참여자 복원
    @Test
    void reconnectProjector_addsParticipant() {
        SessionState state = new ReconnectProjector().apply(SessionState.empty(), event("user1", EventType.RECONNECT, Map.of(), 1));

        assertThat(state.getParticipants()).containsExactly("user1");
    }

    // Sequence: JOIN → LEAVE → JOIN 순서 재생
    @Test
    void sequence_joinLeaveJoin_hasOneParticipant() {
        JoinProjector join = new JoinProjector();
        LeaveProjector leave = new LeaveProjector();

        SessionState state = SessionState.empty();
        state = join.apply(state, event("user1", EventType.JOIN, Map.of(), 1));
        state = leave.apply(state, event("user1", EventType.LEAVE, Map.of(), 2));
        state = join.apply(state, event("user1", EventType.JOIN, Map.of(), 3));

        assertThat(state.getParticipants()).containsExactly("user1");
        assertThat(state.getLastSequenceNo()).isEqualTo(3);
    }

    // Empty: 이벤트 0개 → 빈 SessionState
    @Test
    void emptyState_hasNoParticipantsAndNoMessages() {
        SessionState state = SessionState.empty();

        assertThat(state.getParticipants()).isEmpty();
        assertThat(state.getMessages()).isEmpty();
        assertThat(state.getLastSequenceNo()).isEqualTo(0);
    }

    private ChatEvent event(String userId, EventType type, Map<String, Object> payload, long sequenceNo) {
        return new ChatEvent(
                ChatEventId.create(),
                SESSION_ID,
                userId,
                ClientEventId.of("client-" + sequenceNo),
                type,
                payload,
                sequenceNo,
                NOW.plusSeconds(sequenceNo)
        );
    }
}
