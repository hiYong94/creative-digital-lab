package com.creatived.chat.application.session;

import com.creatived.chat.application.snapshot.FakeChatEventRepository;
import com.creatived.chat.domain.session.AlreadyJoinedException;
import com.creatived.chat.domain.session.InvalidSessionStateException;
import com.creatived.chat.domain.session.Session;
import com.creatived.chat.domain.session.SessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionApplicationServiceTest {

    private SessionApplicationService service;
    private FakeChatEventRepository chatEventRepository;

    @BeforeEach
    void setUp() {
        chatEventRepository = new FakeChatEventRepository();
        service = new SessionApplicationService(new FakeSessionRepository(), chatEventRepository);
    }

    // State transition: 세션 생성 → ACTIVE 상태
    @Test
    void create_returnsActiveSession() {
        Session session = service.create(new CreateSessionCommand("user1"));

        assertThat(session.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(session.getParticipants()).hasSize(1);
    }

    // State transition: ACTIVE 세션 참여 성공
    @Test
    void join_activeSession_addsParticipant() {
        Session session = service.create(new CreateSessionCommand("user1"));

        service.join(new JoinSessionCommand(session.getId().value(), "user2"));

        Session found = service.getById(new GetSessionQuery(session.getId().value()));
        assertThat(found.getParticipants()).hasSize(2);
    }

    // Mirror: ENDED 세션 참여 → 예외
    @Test
    void join_endedSession_throwsInvalidSessionStateException() {
        Session session = service.create(new CreateSessionCommand("user1"));
        service.end(new EndSessionCommand(session.getId().value()));

        assertThatThrownBy(() ->
                service.join(new JoinSessionCommand(session.getId().value(), "user2"))
        ).isInstanceOf(InvalidSessionStateException.class);
    }

    // Invariant: 동일 userId 중복 참여 → 예외
    @Test
    void join_duplicateUserId_throwsAlreadyJoinedException() {
        Session session = service.create(new CreateSessionCommand("user1"));

        assertThatThrownBy(() ->
                service.join(new JoinSessionCommand(session.getId().value(), "user1"))
        ).isInstanceOf(AlreadyJoinedException.class);
    }

    // State transition: ACTIVE → ENDED
    @Test
    void end_activeSession_changesStatusToEnded() {
        Session session = service.create(new CreateSessionCommand("user1"));

        Session ended = service.end(new EndSessionCommand(session.getId().value()));

        assertThat(ended.getStatus().name()).isEqualTo("ENDED");
        assertThat(ended.getEndedAt()).isNotNull();
    }

    // Idempotency: ENDED 세션 재종료 → 동일 ENDED 상태 반환
    @Test
    void end_endedSession_isIdempotent() {
        Session session = service.create(new CreateSessionCommand("user1"));
        service.end(new EndSessionCommand(session.getId().value()));

        Session result = service.end(new EndSessionCommand(session.getId().value()));

        assertThat(result.getStatus().name()).isEqualTo("ENDED");
    }

    // Empty: 존재하지 않는 세션 조회 → 예외
    @Test
    void getById_nonExistentSession_throwsSessionNotFoundException() {
        assertThatThrownBy(() ->
                service.getById(new GetSessionQuery(UUID.randomUUID()))
        ).isInstanceOf(SessionNotFoundException.class);
    }

    // Boundary: 목록 조회 페이지네이션
    @Test
    void getList_pagination_returnsCorrectPage() {
        service.create(new CreateSessionCommand("user1"));
        service.create(new CreateSessionCommand("user2"));
        service.create(new CreateSessionCommand("user3"));

        List<Session> page0 = service.getList(new GetSessionListQuery(0, 2));
        List<Session> page1 = service.getList(new GetSessionListQuery(1, 2));

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(1);
    }
}
