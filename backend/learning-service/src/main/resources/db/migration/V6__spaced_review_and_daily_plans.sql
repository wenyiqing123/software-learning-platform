ALTER TABLE wrong_question ADD COLUMN review_streak INT NOT NULL DEFAULT 0;

CREATE TABLE spaced_review_schedule (
 id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, wrong_question_id BIGINT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', interval_minutes INT NOT NULL DEFAULT 0,
 due_at TIMESTAMP(6) NOT NULL, last_reviewed_at TIMESTAMP(6) NULL, last_attempt_id BIGINT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY (id), UNIQUE KEY uk_spaced_review_wrong (wrong_question_id), KEY idx_spaced_review_due (user_id,state,due_at),
 CONSTRAINT fk_spaced_review_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_spaced_review_wrong FOREIGN KEY (wrong_question_id) REFERENCES wrong_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE spaced_review_log (
 id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, wrong_question_id BIGINT NOT NULL,
 request_key CHAR(36) NOT NULL, attempt_id BIGINT NOT NULL, recall_rating TINYINT NOT NULL,
 is_correct TINYINT(1) NOT NULL, interval_minutes INT NOT NULL, reviewed_at TIMESTAMP(6) NOT NULL,
 next_due_at TIMESTAMP(6) NOT NULL, response_json TEXT NOT NULL, request_json TEXT NOT NULL,
 PRIMARY KEY (id), UNIQUE KEY uk_spaced_review_request (user_id,request_key), KEY idx_spaced_review_log_user_date (user_id,reviewed_at),
 CONSTRAINT fk_spaced_review_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_spaced_review_log_wrong FOREIGN KEY (wrong_question_id) REFERENCES wrong_question(id) ON DELETE CASCADE,
 CONSTRAINT fk_spaced_review_log_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempt(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE daily_study_plan (
 id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, plan_date DATE NOT NULL, budget_minutes SMALLINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY (id), UNIQUE KEY uk_daily_plan_user_date (user_id,plan_date),
 CONSTRAINT fk_daily_plan_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE daily_study_task (
 id BIGINT NOT NULL AUTO_INCREMENT, plan_id BIGINT NOT NULL, user_id BIGINT NOT NULL, task_type VARCHAR(16) NOT NULL,
 target_id BIGINT NOT NULL, target_title VARCHAR(300) NOT NULL, target_detail VARCHAR(500) NOT NULL,
 minutes SMALLINT NOT NULL, reason VARCHAR(300) NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 sort_order SMALLINT NOT NULL, course_id BIGINT NULL, completed_at TIMESTAMP(6) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY (id),
 UNIQUE KEY uk_daily_task_plan_target (plan_id,task_type,target_id), KEY idx_daily_task_user_plan_state (user_id,plan_id,state,sort_order),
 CONSTRAINT fk_daily_task_plan FOREIGN KEY (plan_id) REFERENCES daily_study_plan(id) ON DELETE CASCADE,
 CONSTRAINT fk_daily_task_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_daily_task_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
