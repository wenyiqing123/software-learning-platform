CREATE TABLE quiz_option_diagnosis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    option_key VARCHAR(12) NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    rationale VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quiz_option_diagnosis (question_id, option_key),
    KEY idx_quiz_option_diagnosis_point (knowledge_point_id),
    CONSTRAINT fk_quiz_option_diagnosis_question FOREIGN KEY (question_id) REFERENCES quiz_question(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_option_diagnosis_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
