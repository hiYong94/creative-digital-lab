CREATE TABLE IF NOT EXISTS sessions
(
    id         BINARY(16)  NOT NULL PRIMARY KEY COMMENT '세션 고유 식별자',
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '세션 상태 (ACTIVE | ENDED)',
    created_at DATETIME(6) NOT NULL DEFAULT NOW(6) COMMENT '세션 생성 시각',
    ended_at   DATETIME(6) NULL COMMENT '세션 종료 시각. ACTIVE 상태일 때 null',
    INDEX idx_sessions_status_created (status, created_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS participants
(
    id         BINARY(16)  NOT NULL PRIMARY KEY COMMENT '참여자 고유 식별자',
    session_id BINARY(16)  NOT NULL COMMENT '소속 세션 식별자',
    user_id    VARCHAR(64) NOT NULL COMMENT '참여자 식별자. 인증 없이 클라이언트가 제공하는 문자열',
    joined_at  DATETIME(6) NOT NULL DEFAULT NOW(6) COMMENT '세션 참여 시각',
    left_at    DATETIME(6) NULL COMMENT '세션 퇴장 시각. 참여 중일 때 null',
    CONSTRAINT uq_participant UNIQUE (session_id, user_id),
    CONSTRAINT fk_participant_session FOREIGN KEY (session_id) REFERENCES sessions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS chat_events
(
    id                 BINARY(16)  NOT NULL PRIMARY KEY COMMENT '이벤트 고유 식별자',
    session_id         BINARY(16)  NOT NULL COMMENT '소속 세션 식별자',
    user_id            VARCHAR(64) NOT NULL COMMENT '이벤트 발생 참여자 식별자',
    client_event_id    VARCHAR(64) NOT NULL COMMENT '클라이언트 발급 이벤트 식별자. Idempotency 키로 사용',
    type               VARCHAR(20) NOT NULL COMMENT '이벤트 타입 (JOIN | LEAVE | MESSAGE | DISCONNECT | RECONNECT)',
    payload            JSON        NOT NULL COMMENT '이벤트 타입별 추가 데이터',
    sequence_no        BIGINT      NOT NULL COMMENT '세션 내 이벤트 순서. serverReceivedAt 동일 시각 tie-break에 사용',
    server_received_at DATETIME(6) NOT NULL DEFAULT NOW(6) COMMENT '서버 수신 시각. 이벤트 정렬 1순위 기준',
    CONSTRAINT uq_event_idempotency UNIQUE (session_id, client_event_id),
    CONSTRAINT fk_event_session FOREIGN KEY (session_id) REFERENCES sessions (id),
    INDEX idx_events_session_time (session_id, server_received_at),
    INDEX idx_events_session_sequence (session_id, sequence_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS snapshots
(
    id               BINARY(16)  NOT NULL PRIMARY KEY COMMENT 'Snapshot 고유 식별자',
    session_id       BINARY(16)  NOT NULL COMMENT '소속 세션 식별자',
    last_event_id    BINARY(16)  NULL COMMENT 'Snapshot 생성 시점의 마지막 이벤트 식별자',
    state            JSON        NOT NULL COMMENT 'Snapshot 시점의 세션 상태 (participants, messages) JSON 직렬화',
    last_sequence_no BIGINT      NOT NULL COMMENT 'Snapshot 생성 시점의 마지막 sequenceNo. Delta Replay 시작점으로 사용',
    created_at       DATETIME(6) NOT NULL DEFAULT NOW(6) COMMENT 'Snapshot 생성 시각',
    CONSTRAINT fk_snapshot_session FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT fk_snapshot_event FOREIGN KEY (last_event_id) REFERENCES chat_events (id),
    INDEX idx_snapshots_session_time (session_id, created_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
