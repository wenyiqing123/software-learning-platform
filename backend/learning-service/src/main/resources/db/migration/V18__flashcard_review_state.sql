CREATE TABLE flashcard_review_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    recall_rating TINYINT NOT NULL DEFAULT 2,
    review_count INT NOT NULL DEFAULT 0,
    interval_minutes INT NOT NULL DEFAULT 1440,
    next_due_at TIMESTAMP(6) NOT NULL,
    last_reviewed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_flashcard_user_point (user_id, knowledge_point_id),
    KEY idx_flashcard_user_due (user_id, next_due_at),
    CONSTRAINT fk_flashcard_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_flashcard_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
