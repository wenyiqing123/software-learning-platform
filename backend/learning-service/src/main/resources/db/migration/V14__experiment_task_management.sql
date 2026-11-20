CREATE TABLE experiment_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    experiment_type VARCHAR(20) NOT NULL DEFAULT 'METHODS',
    question VARCHAR(2000) NOT NULL,
    reference_answer VARCHAR(4000) NOT NULL DEFAULT '',
    expected_entities_json JSON NOT NULL,
    include_ablations BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    starts_at TIMESTAMP(6) NULL,
    ends_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_experiment_task_status_created (status, created_at),
    CONSTRAINT fk_experiment_task_creator FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_experiment_task_type CHECK (experiment_type IN ('METHODS', 'QWEN_MATRIX')),
    CONSTRAINT ck_experiment_task_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE experiment_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    condition_key VARCHAR(80) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_experiment_group_task_condition (task_id, condition_key),
    CONSTRAINT fk_experiment_group_task FOREIGN KEY (task_id) REFERENCES experiment_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE experiment_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_experiment_assignment_task_user (task_id, user_id),
    KEY idx_experiment_assignment_user_status (user_id, status),
    CONSTRAINT fk_experiment_assignment_task FOREIGN KEY (task_id) REFERENCES experiment_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_experiment_assignment_group FOREIGN KEY (group_id) REFERENCES experiment_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_experiment_assignment_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT ck_experiment_assignment_status CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE ai_experiment_run
    ADD COLUMN task_id BIGINT NULL AFTER user_id,
    ADD COLUMN assignment_id BIGINT NULL AFTER task_id,
    ADD KEY idx_ai_experiment_task_assignment (task_id, assignment_id),
    ADD CONSTRAINT fk_ai_experiment_task FOREIGN KEY (task_id) REFERENCES experiment_task(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_ai_experiment_assignment FOREIGN KEY (assignment_id) REFERENCES experiment_assignment(id) ON DELETE SET NULL;
