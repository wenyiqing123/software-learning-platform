-- Question-bank imports are staged by version and become student-visible only after publication.
CREATE TABLE question_bank_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    version_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source_filename VARCHAR(255) NOT NULL DEFAULT '',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_question_bank_version_course_status (course_id, status, id),
    CONSTRAINT fk_question_bank_version_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_bank_version_creator FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE RESTRICT,
    CONSTRAINT ck_question_bank_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE quiz_question
    ADD COLUMN version_id BIGINT NULL AFTER source,
    ADD KEY idx_question_version_enabled (version_id, enabled),
    ADD CONSTRAINT fk_question_version FOREIGN KEY (version_id) REFERENCES question_bank_version(id) ON DELETE SET NULL;

CREATE TABLE question_bank_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    source_filename VARCHAR(255) NOT NULL DEFAULT '',
    total_rows INT NOT NULL,
    success_count INT NOT NULL,
    error_count INT NOT NULL,
    error_details_json JSON NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_question_bank_import_version_created (version_id, created_at),
    CONSTRAINT fk_question_bank_import_version FOREIGN KEY (version_id) REFERENCES question_bank_version(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_bank_import_creator FOREIGN KEY (created_by) REFERENCES sys_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
