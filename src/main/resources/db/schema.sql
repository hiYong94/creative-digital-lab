CREATE TABLE IF NOT EXISTS sessions
(
    id         BINARY(16)  NOT NULL PRIMARY KEY,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT NOW(6),
    ended_at   DATETIME(6) NULL,
    INDEX idx_sessions_status_created (status, created_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS participants
(
    id         BINARY(16)  NOT NULL PRIMARY KEY,
    session_id BINARY(16)  NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    joined_at  DATETIME(6) NOT NULL DEFAULT NOW(6),
    left_at    DATETIME(6) NULL,
    CONSTRAINT uq_participant UNIQUE (session_id, user_id),
    CONSTRAINT fk_participant_session FOREIGN KEY (session_id) REFERENCES sessions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS chat_events
(
    id                 BINARY(16)  NOT NULL PRIMARY KEY,
    session_id         BINARY(16)  NOT NULL,
    user_id            VARCHAR(64) NOT NULL,
    client_event_id    VARCHAR(64) NOT NULL,
    type               VARCHAR(20) NOT NULL,
    payload            JSON        NOT NULL,
    sequence_no        BIGINT      NOT NULL,
    server_received_at DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT uq_event_idempotency UNIQUE (session_id, client_event_id),
    CONSTRAINT fk_event_session FOREIGN KEY (session_id) REFERENCES sessions (id),
    INDEX idx_events_session_time (session_id, server_received_at),
    INDEX idx_events_session_sequence (session_id, sequence_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS snapshots
(
    id               BINARY(16)  NOT NULL PRIMARY KEY,
    session_id       BINARY(16)  NOT NULL,
    last_event_id    BINARY(16)  NULL,
    state            JSON        NOT NULL,
    last_sequence_no BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL DEFAULT NOW(6),
    CONSTRAINT fk_snapshot_session FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT fk_snapshot_event FOREIGN KEY (last_event_id) REFERENCES chat_events (id),
    INDEX idx_snapshots_session_time (session_id, created_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
