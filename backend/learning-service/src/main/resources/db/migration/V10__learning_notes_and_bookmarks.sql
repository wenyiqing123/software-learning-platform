CREATE TABLE learning_note (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    section_id BIGINT NULL,
    knowledge_point_id BIGINT NULL,
    title VARCHAR(160) NOT NULL DEFAULT '',
    content TEXT NOT NULL,
    tags_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_learning_note_user_updated (user_id, updated_at),
    KEY idx_learning_note_user_section (user_id, section_id),
    KEY idx_learning_note_user_point (user_id, knowledge_point_id),
    CONSTRAINT fk_learning_note_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_note_section FOREIGN KEY (section_id) REFERENCES course_study_section(id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_note_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_section_bookmark (
    user_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, section_id),
    CONSTRAINT fk_section_bookmark_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_section_bookmark_section FOREIGN KEY (section_id) REFERENCES course_study_section(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_point_bookmark (
    user_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, knowledge_point_id),
    CONSTRAINT fk_point_bookmark_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_point_bookmark_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
