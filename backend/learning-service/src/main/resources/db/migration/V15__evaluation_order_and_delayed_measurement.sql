ALTER TABLE learning_evaluation_session
    ADD COLUMN task_order VARCHAR(120) NULL,
    ADD COLUMN assignment_id BIGINT NULL,
    ADD KEY idx_eval_session_assignment (assignment_id);

-- The existing measurement table is phase-keyed. DELAYED is an additional
-- valid phase and therefore needs no table rewrite or new sensitive fields.
