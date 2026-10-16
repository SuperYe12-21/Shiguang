USE shiguang;

CREATE TABLE IF NOT EXISTS transcode_task (
    post_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    lease_token VARCHAR(36) NOT NULL DEFAULT '',
    lease_until DATETIME(3) NULL,
    last_error VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (post_id),
    KEY idx_dispatch (state, next_attempt_at),
    KEY idx_lease (state, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO transcode_task (post_id)
SELECT id FROM post WHERE type = 'VIDEO' AND status = 'PROCESSING';
