DROP ALL OBJECTS;
CREATE TABLE sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(60) UNIQUE NOT NULL, password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(80) NOT NULL, role VARCHAR(20) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE);
CREATE TABLE course (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(40) NOT NULL, name VARCHAR(120) NOT NULL, term VARCHAR(40) NOT NULL, description VARCHAR(500) NOT NULL, graph_space_id INT DEFAULT 1, enabled BOOLEAN DEFAULT TRUE, UNIQUE(code, term));
CREATE TABLE course_chapter (id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id BIGINT NOT NULL REFERENCES course(id), name VARCHAR(120) NOT NULL, description VARCHAR(500) NOT NULL, sort_order INT NOT NULL);
CREATE TABLE knowledge_point (id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id BIGINT NOT NULL REFERENCES course(id), chapter_id BIGINT REFERENCES course_chapter(id), name VARCHAR(160), graph_entity VARCHAR(200), description VARCHAR(800), prerequisites_json CLOB, sort_order INT, enabled BOOLEAN DEFAULT TRUE);
CREATE TABLE knowledge_mastery (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, knowledge_point_id BIGINT NOT NULL, mastery_score DECIMAL(6,2), peak_mastery DECIMAL(6,2) NOT NULL DEFAULT 0, attempt_count INT, correct_count INT, last_studied_at TIMESTAMP);
CREATE TABLE quiz_question (id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id BIGINT NOT NULL REFERENCES course(id), knowledge_point_id BIGINT REFERENCES knowledge_point(id), question_type VARCHAR(20), stem CLOB, options_json CLOB, answer_json CLOB, explanation CLOB, difficulty INT, source VARCHAR(40), version_id BIGINT, enabled BOOLEAN DEFAULT TRUE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
INSERT INTO sys_user VALUES (1,'student-one','unused','学生一','STUDENT',TRUE),(2,'student-two','unused','学生二','STUDENT',TRUE);
INSERT INTO course VALUES (1,'SE-2026-FALL','软件工程','2026 秋季','测试课程',1,TRUE),(2,'OTHER','其他课程','2026 秋季','其他课程',1,TRUE);
INSERT INTO course_chapter VALUES (1,1,'需求分析与建模','需求分析、数据流图和 UML 表达',1),(2,1,'系统设计与软件生命周期','系统设计阶段、交付文档与维护',2);
INSERT INTO knowledge_point VALUES
(1,1,1,'需求分析','需求分析','需求',NULL,1,TRUE),
(2,1,1,'数据流图 DFD','数据流图DFD','DFD',NULL,2,TRUE),
(3,1,1,'UML 用例图','用例图','UML',NULL,3,TRUE),
(4,1,1,'软件需求规格说明书','软件需求规格说明书','SRS',NULL,4,TRUE),
(5,1,1,'数据存储','数据存储','存储',NULL,5,TRUE),
(6,1,2,'软件生命周期','软件生命周期','生命周期',NULL,1,TRUE),
(7,1,2,'软件维护','软件维护','维护',NULL,2,TRUE),
(8,1,2,'机票预定系统','机票预定系统','案例',NULL,3,TRUE),
(9,2,NULL,'其他知识点','OTHER','其他',NULL,1,TRUE);
