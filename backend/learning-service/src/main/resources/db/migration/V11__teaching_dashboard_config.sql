CREATE TABLE teaching_dashboard_config (
    course_id BIGINT NOT NULL,
    default_days SMALLINT NOT NULL DEFAULT 7,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (course_id),
    CONSTRAINT fk_teaching_dashboard_config_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO teaching_dashboard_config (course_id, default_days, enabled)
SELECT id, 7, TRUE FROM course WHERE enabled=TRUE
ON DUPLICATE KEY UPDATE course_id=VALUES(course_id);

