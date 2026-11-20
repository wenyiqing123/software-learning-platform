CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(60) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    term VARCHAR(40) NOT NULL,
    description VARCHAR(500) NOT NULL,
    graph_space_id INT NOT NULL DEFAULT 1,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_code_term (code, term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE course_chapter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_chapter_course_order (course_id, sort_order),
    CONSTRAINT fk_chapter_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_point (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NULL,
    name VARCHAR(160) NOT NULL,
    graph_entity VARCHAR(200) NOT NULL,
    description VARCHAR(800) NOT NULL DEFAULT '',
    prerequisites_json JSON NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_course_graph_entity (course_id, graph_entity),
    KEY idx_knowledge_chapter_order (chapter_id, sort_order),
    CONSTRAINT fk_knowledge_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapter(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NULL,
    question_type VARCHAR(20) NOT NULL,
    stem TEXT NOT NULL,
    options_json JSON NULL,
    answer_json JSON NOT NULL,
    explanation TEXT NOT NULL,
    difficulty TINYINT NOT NULL DEFAULT 2,
    source VARCHAR(40) NOT NULL DEFAULT 'ADMIN',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_question_course_enabled (course_id, enabled),
    KEY idx_question_knowledge_point (knowledge_point_id),
    CONSTRAINT fk_question_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quiz_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    submitted_answer_json JSON NOT NULL,
    is_correct TINYINT(1) NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_attempt_user_date (user_id, submitted_at),
    KEY idx_attempt_user_question (user_id, question_id),
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_question FOREIGN KEY (question_id) REFERENCES quiz_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wrong_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    wrong_count INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'UNMASTERED',
    last_wrong_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    mastered_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wrong_user_question (user_id, question_id),
    KEY idx_wrong_user_status_date (user_id, status, last_wrong_at),
    CONSTRAINT fk_wrong_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_wrong_question FOREIGN KEY (question_id) REFERENCES quiz_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at TIMESTAMP(6) NULL,
    processed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_learning_event_id (event_id),
    KEY idx_learning_event_user_date (user_id, created_at),
    CONSTRAINT fk_learning_event_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_mastery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    mastery_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    last_studied_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mastery_user_point (user_id, knowledge_point_id),
    CONSTRAINT fk_mastery_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_mastery_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NULL,
    question_id BIGINT NULL,
    recommendation_type VARCHAR(24) NOT NULL,
    priority_score DECIMAL(6,3) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_recommendation_user_date (user_id, generated_at),
    CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_question FOREIGN KEY (question_id) REFERENCES quiz_question(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO course (code, name, term, description, graph_space_id)
VALUES ('SE-2026-FALL', '软件工程', '2026 秋季', '围绕需求分析、系统建模、设计与软件维护的课程学习空间。', 1);

INSERT INTO course_chapter (course_id, name, description, sort_order)
SELECT id, '需求分析与建模', '需求分析、数据流图和 UML 表达', 1 FROM course WHERE code = 'SE-2026-FALL' AND term = '2026 秋季';
INSERT INTO course_chapter (course_id, name, description, sort_order)
SELECT id, '系统设计与软件生命周期', '系统设计阶段、交付文档与维护', 2 FROM course WHERE code = 'SE-2026-FALL' AND term = '2026 秋季';

INSERT INTO knowledge_point (course_id, chapter_id, name, graph_entity, description, sort_order)
SELECT c.id, ch.id, seed.name, seed.graph_entity, seed.description, seed.sort_order
FROM course c JOIN course_chapter ch ON ch.course_id = c.id
JOIN (
  SELECT '需求分析' name, '需求分析' graph_entity, '分析用户问题、确定软件需求并形成需求规格说明书。' description, 1 sort_order, 1 chapter_order
  UNION ALL SELECT '数据流图 DFD', '数据流图DFD', '描述数据在系统中的流动与处理过程。', 2, 1
  UNION ALL SELECT 'UML 用例图', '用例图', '刻画参与者与系统功能目标之间的关系。', 3, 1
  UNION ALL SELECT '软件需求规格说明书', '软件需求规格说明书', '记录经过分析确认的软件需求及相关约束。', 4, 1
  UNION ALL SELECT '数据存储', '数据存储', '数据流图中用于表示静态数据集合的组成元素。', 5, 1
  UNION ALL SELECT '软件生命周期', '软件生命周期', '从问题定义、需求分析到设计、实现、运行维护的阶段过程。', 1, 2
  UNION ALL SELECT '软件维护', '软件维护', '软件交付后为纠错、适应变化和完善功能而进行的工作。', 2, 2
  UNION ALL SELECT '机票预定系统', '机票预定系统', '贯穿需求分析、数据流图与 UML 建模的课程案例。', 3, 2
) seed ON seed.chapter_order = ch.sort_order
WHERE c.code = 'SE-2026-FALL' AND c.term = '2026 秋季';

INSERT INTO quiz_question (course_id, knowledge_point_id, question_type, stem, options_json, answer_json, explanation, difficulty, source)
SELECT c.id, kp.id, 'SINGLE', '数据流图中，加工的命名通常应采用哪种形式？',
       JSON_ARRAY(JSON_OBJECT('key','A','text','名词或名词短语'), JSON_OBJECT('key','B','text','动词 + 宾语的动宾短语'), JSON_OBJECT('key','C','text','主语 + 谓语的主谓短语'), JSON_OBJECT('key','D','text','任意简短描述')),
       JSON_ARRAY('B'), '加工表示对输入数据执行的变换，采用动宾短语能够明确表达处理动作和对象。', 1, 'COURSE_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id = c.id AND kp.graph_entity = '数据流图DFD'
WHERE c.code = 'SE-2026-FALL' AND c.term = '2026 秋季';

INSERT INTO quiz_question (course_id, knowledge_point_id, question_type, stem, options_json, answer_json, explanation, difficulty, source)
SELECT c.id, kp.id, 'SINGLE', 'UML 用例图主要用于描述什么？',
       JSON_ARRAY(JSON_OBJECT('key','A','text','对象之间的消息时序'), JSON_OBJECT('key','B','text','参与者与系统功能目标之间的关系'), JSON_OBJECT('key','C','text','类的字段在内存中的布局'), JSON_OBJECT('key','D','text','程序运行时的线程调度')),
       JSON_ARRAY('B'), '用例图从用户目标出发，展示参与者与系统用例以及它们之间的关系。', 1, 'COURSE_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id = c.id AND kp.graph_entity = '用例图'
WHERE c.code = 'SE-2026-FALL' AND c.term = '2026 秋季';

INSERT INTO quiz_question (course_id, knowledge_point_id, question_type, stem, options_json, answer_json, explanation, difficulty, source)
SELECT c.id, kp.id, 'SINGLE', '软件需求分析阶段通常形成的核心文档是什么？',
       JSON_ARRAY(JSON_OBJECT('key','A','text','软件需求规格说明书'), JSON_OBJECT('key','B','text','用户操作系统内核'), JSON_OBJECT('key','C','text','编译器词法表'), JSON_OBJECT('key','D','text','硬件装配清单')),
       JSON_ARRAY('A'), '需求分析阶段将确认后的功能、质量属性和约束整理为软件需求规格说明书。', 1, 'COURSE_SEED'
FROM course c JOIN knowledge_point kp ON kp.course_id = c.id AND kp.graph_entity = '需求分析'
WHERE c.code = 'SE-2026-FALL' AND c.term = '2026 秋季';
