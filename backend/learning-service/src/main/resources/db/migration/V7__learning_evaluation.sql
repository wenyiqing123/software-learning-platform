CREATE TABLE learning_evaluation_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    condition_key VARCHAR(60) NOT NULL,
    task_key VARCHAR(120) NOT NULL,
    consent_version VARCHAR(30) NOT NULL,
    consented_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    elapsed_seconds INT NULL,
    psychological_effort TINYINT NULL,
    feedback TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_eval_session_user_date (user_id, created_at),
    KEY idx_eval_session_condition_status (condition_key, completed_at),
    CONSTRAINT fk_eval_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_evaluation_measurement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    phase VARCHAR(16) NOT NULL,
    score DECIMAL(6,2) NOT NULL,
    responses_json TEXT NOT NULL,
    measured_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_eval_measurement_session_phase (session_id, phase),
    KEY idx_eval_measurement_user_date (user_id, measured_at),
    CONSTRAINT fk_eval_measurement_session FOREIGN KEY (session_id) REFERENCES learning_evaluation_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_measurement_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
