package edu.wfust.learning.platform.adminuser;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import edu.wfust.learning.platform.config.ApiExceptionHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(AdminUserWorkflowTest.Config.class)
@WebAppConfiguration
class AdminUserWorkflowTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @EnableWebSecurity
    @ComponentScan(
            basePackages = {"edu.wfust.learning.platform.adminuser"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Config.class))
    @MapperScan(basePackages = {"edu.wfust.learning.platform.mapper", "edu.wfust.learning.platform.auth"})
    static class Config {
        @Bean
        DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:admin-user-workflow;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        JdbcTemplate jdbc(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }

        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable())
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .build();
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
            CREATE TABLE sys_user (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              username VARCHAR(60) UNIQUE NOT NULL,
              password_hash VARCHAR(100) NOT NULL,
              display_name VARCHAR(80) NOT NULL,
              role VARCHAR(20) NOT NULL,
              enabled BOOLEAN NOT NULL DEFAULT TRUE,
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");
        jdbc.execute("""
            CREATE TABLE auth_refresh_token (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              token_hash VARCHAR(64) NOT NULL,
              expires_at TIMESTAMP NOT NULL,
              revoked_at TIMESTAMP NULL,
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");
        jdbc.execute("""
            CREATE TABLE course (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              code VARCHAR(40) NOT NULL,
              name VARCHAR(120) NOT NULL,
              term VARCHAR(40) NOT NULL,
              description VARCHAR(500) NOT NULL,
              graph_space_id INT DEFAULT 1,
              enabled BOOLEAN DEFAULT TRUE
            )""");
        jdbc.execute("""
            CREATE TABLE course_chapter (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              course_id BIGINT NOT NULL,
              name VARCHAR(120) NOT NULL,
              description VARCHAR(500) NOT NULL,
              sort_order INT DEFAULT 0
            )""");
        jdbc.execute("""
            CREATE TABLE knowledge_point (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              course_id BIGINT NOT NULL,
              chapter_id BIGINT NULL,
              name VARCHAR(160) NOT NULL,
              graph_entity VARCHAR(200) NOT NULL,
              description VARCHAR(800) DEFAULT '',
              sort_order INT DEFAULT 0,
              enabled BOOLEAN DEFAULT TRUE
            )""");
        jdbc.execute("""
            CREATE TABLE course_study_section (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              course_id BIGINT NOT NULL,
              chapter_id BIGINT NOT NULL,
              knowledge_point_id BIGINT NOT NULL,
              title VARCHAR(160) NOT NULL,
              objectives_json CLOB NOT NULL,
              content_json CLOB NOT NULL,
              resources_json CLOB NOT NULL,
              sort_order INT DEFAULT 0
            )""");
        jdbc.execute("""
            CREATE TABLE course_study_progress (
              user_id BIGINT NOT NULL,
              section_id BIGINT NOT NULL,
              completed BOOLEAN NOT NULL DEFAULT FALSE,
              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (user_id, section_id)
            )""");
        jdbc.execute("""
            CREATE TABLE knowledge_mastery (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              knowledge_point_id BIGINT NOT NULL,
              mastery_score DECIMAL(5,2) NOT NULL DEFAULT 0,
              attempt_count INT NOT NULL DEFAULT 0,
              correct_count INT NOT NULL DEFAULT 0,
              last_studied_at TIMESTAMP NULL
            )""");
        jdbc.execute("""
            CREATE TABLE quiz_question (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              course_id BIGINT NOT NULL,
              knowledge_point_id BIGINT NULL,
              question_type VARCHAR(20) NOT NULL,
              stem CLOB NOT NULL,
              options_json CLOB NULL,
              answer_json CLOB NOT NULL,
              explanation CLOB NOT NULL,
              difficulty INT DEFAULT 2,
              source VARCHAR(40) DEFAULT 'ADMIN',
              enabled BOOLEAN DEFAULT TRUE
            )""");
        jdbc.execute("""
            CREATE TABLE quiz_attempt (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              question_id BIGINT NOT NULL,
              submitted_answer_json CLOB NOT NULL,
              is_correct BOOLEAN NOT NULL,
              submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");
        jdbc.execute("""
            CREATE TABLE wrong_question (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              question_id BIGINT NOT NULL,
              wrong_count INT NOT NULL DEFAULT 1,
              status VARCHAR(20) NOT NULL DEFAULT 'UNMASTERED',
              review_streak INT NOT NULL DEFAULT 0,
              last_wrong_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              mastered_at TIMESTAMP NULL
            )""");
        jdbc.execute("""
            CREATE TABLE spaced_review_schedule (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              wrong_question_id BIGINT NOT NULL,
              state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
              interval_minutes INT NOT NULL DEFAULT 0,
              due_at TIMESTAMP NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE experiment_task (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              title VARCHAR(160) NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE experiment_group (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              task_id BIGINT NOT NULL,
              name VARCHAR(100) NOT NULL,
              condition_key VARCHAR(80) NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE experiment_assignment (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              task_id BIGINT NOT NULL,
              group_id BIGINT NOT NULL,
              user_id BIGINT NOT NULL,
              status VARCHAR(20) NOT NULL,
              assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              started_at TIMESTAMP NULL,
              completed_at TIMESTAMP NULL
            )""");
        jdbc.execute("""
            CREATE TABLE ai_session (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              title VARCHAR(120) NOT NULL,
              updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");
        jdbc.execute("""
            CREATE TABLE ai_message (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              session_id BIGINT NOT NULL,
              role VARCHAR(20) NOT NULL,
              content CLOB NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE learning_note (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              title VARCHAR(120) NOT NULL,
              content CLOB NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE learning_evaluation_session (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              feedback CLOB NULL
            )""");

        jdbc.update("INSERT INTO sys_user (id, username, password_hash, display_name, role, enabled) VALUES "
                + "(1, 'student01', 'hash', '张同学', 'STUDENT', TRUE),"
                + "(2, 'student02', 'hash', '李同学', 'STUDENT', FALSE),"
                + "(3, 'admin01', 'hash', '课程管理员', 'ADMIN', TRUE)");
        jdbc.update("INSERT INTO auth_refresh_token (id, user_id, token_hash, expires_at, revoked_at) VALUES "
                + "(10, 1, 'tok1', DATEADD('DAY', 10, CURRENT_TIMESTAMP), NULL)");
        jdbc.update("INSERT INTO course (id, code, name, term, description, enabled) VALUES "
                + "(1, 'SE-2026', '软件工程', '2026 秋季', '课程', TRUE)");
        jdbc.update("INSERT INTO course_chapter (id, course_id, name, description, sort_order) VALUES "
                + "(1, 1, '需求分析与建模', '说明', 1)");
        jdbc.update("INSERT INTO knowledge_point (id, course_id, chapter_id, name, graph_entity, enabled) VALUES "
                + "(1, 1, 1, '需求分析', '需求分析', TRUE),"
                + "(2, 1, 1, '数据流图 DFD', '数据流图DFD', TRUE)");
        jdbc.update("INSERT INTO course_study_section (id, course_id, chapter_id, knowledge_point_id, title, objectives_json, content_json, resources_json, sort_order) VALUES "
                + "(1, 1, 1, 1, '需求分析节', '[]', '[]', '[]', 1),"
                + "(2, 1, 1, 2, 'DFD节', '[]', '[]', '[]', 2)");
        jdbc.update("INSERT INTO course_study_progress (user_id, section_id, completed) VALUES (1, 1, TRUE)");
        jdbc.update("INSERT INTO knowledge_mastery (user_id, knowledge_point_id, mastery_score, attempt_count, correct_count, last_studied_at) VALUES "
                + "(1, 1, 85.00, 4, 4, CURRENT_TIMESTAMP),"
                + "(1, 2, 42.50, 3, 1, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO quiz_question (id, course_id, knowledge_point_id, question_type, stem, options_json, answer_json, explanation) VALUES "
                + "(1, 1, 2, 'SINGLE', '数据流图中加工如何命名？', '[]', '[\"B\"]', '解析')");
        jdbc.update("INSERT INTO quiz_attempt (user_id, question_id, submitted_answer_json, is_correct) VALUES "
                + "(1, 1, '[\"A\"]', FALSE), (1, 1, '[\"B\"]', TRUE)");
        jdbc.update("INSERT INTO wrong_question (id, user_id, question_id, wrong_count, status, review_streak) VALUES "
                + "(1, 1, 1, 2, 'UNMASTERED', 0)");
        jdbc.update("INSERT INTO spaced_review_schedule (id, user_id, wrong_question_id, state, interval_minutes, due_at) VALUES "
                + "(1, 1, 1, 'ACTIVE', 30, DATEADD('MINUTE', -5, CURRENT_TIMESTAMP))");
        jdbc.update("INSERT INTO experiment_task (id, title) VALUES (1, '数据流图实验')");
        jdbc.update("INSERT INTO experiment_group (id, task_id, name, condition_key) VALUES (1, 1, '实验组A', 'RANKED')");
        jdbc.update("INSERT INTO experiment_assignment (id, task_id, group_id, user_id, status) VALUES (1, 1, 1, 1, 'IN_PROGRESS')");
        jdbc.update("INSERT INTO ai_session (id, user_id, title) VALUES (1, 1, 'DFD 加工命名疑惑')");
        jdbc.update("INSERT INTO ai_message (id, session_id, role, content) VALUES (1, 1, 'USER', 'SECRET_AI_QUESTION_TEXT')");
        jdbc.update("INSERT INTO learning_note (id, user_id, title, content) VALUES (1, 1, '私人笔记', 'PRIVATE_STUDENT_NOTE_SECRET')");
        jdbc.update("INSERT INTO learning_evaluation_session (id, user_id, feedback) VALUES (1, 1, 'SENSITIVE_EVAL_FEEDBACK')");

        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rejectsNonAdminUsersFromUserManagementEndpoints() throws Exception {
        mvc.perform(get("/api/admin/users").with(user("1").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/1/profile").with(user("1").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/1/enabled").with(user("1").roles("STUDENT"))
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsUsersWithFiltersAndAggregatedLearningSummaries() throws Exception {
        mvc.perform(get("/api/admin/users").param("keyword", "张").param("role", "STUDENT").with(user("3").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("student01"))
                .andExpect(jsonPath("$.items[0].completedSections").value(1))
                .andExpect(jsonPath("$.items[0].attemptCount").value(2))
                .andExpect(jsonPath("$.items[0].correctCount").value(1))
                .andExpect(jsonPath("$.items[0].accuracy").value(50))
                .andExpect(jsonPath("$.items[0].openWrongCount").value(1))
                .andExpect(jsonPath("$.items[0].dueReviewCount").value(1))
                .andExpect(jsonPath("$.items[0].activeTokenCount").value(1));

        mvc.perform(get("/api/admin/users").param("enabled", "false").with(user("3").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("student02"));
    }

    @Test
    void createsUpdatesResetsPasswordAndEnforcesSelfProtectionGuards() throws Exception {
        mvc.perform(post("/api/admin/users").with(user("3").roles("ADMIN"))
                        .contentType("application/json")
                        .content("{\"username\":\"student03\",\"password\":\"Passw0rd!23\",\"displayName\":\"王同学\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("student03"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(post("/api/admin/users").with(user("3").roles("ADMIN"))
                        .contentType("application/json")
                        .content("{\"username\":\"student01\",\"password\":\"Passw0rd!23\",\"displayName\":\"重复\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isConflict());

        // 不能停用自己，也不能降级自己角色
        mvc.perform(put("/api/admin/users/3/enabled").with(user("3").roles("ADMIN"))
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/3").with(user("3").roles("ADMIN"))
                        .contentType("application/json").content("{\"displayName\":\"管理员\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest());

        // 重置他人密码并停用会吊销所有活跃 Refresh Token
        mvc.perform(put("/api/admin/users/1/password").with(user("3").roles("ADMIN"))
                        .contentType("application/json").content("{\"newPassword\":\"ResetPass!99\"}"))
                .andExpect(status().isNoContent());

        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id = 1", String.class);
        assertThat(passwordEncoder.matches("ResetPass!99", hash)).isTrue();
        Integer activeTokens = jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_token WHERE user_id = 1 AND revoked_at IS NULL", Integer.class);
        assertThat(activeTokens).isZero();
    }

    @Test
    void returnsWhitelistUserProfileWithoutPrivateNotesEvaluationOrAiMessageBodies() throws Exception {
        String body = mvc.perform(get("/api/admin/users/1/profile").with(user("3").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.username").value("student01"))
                .andExpect(jsonPath("$.account.activeSessionCount").value(1))
                .andExpect(jsonPath("$.summary.completedSections").value(1))
                .andExpect(jsonPath("$.summary.totalSections").value(2))
                .andExpect(jsonPath("$.summary.openWrongCount").value(1))
                .andExpect(jsonPath("$.summary.dueReviewCount").value(1))
                .andExpect(jsonPath("$.summary.aiSessionCount").value(1))
                .andExpect(jsonPath("$.summary.aiQuestionCount").value(1))
                .andExpect(jsonPath("$.courses[0].courseName").value("软件工程"))
                .andExpect(jsonPath("$.knowledgeMastery.length()").value(2))
                .andExpect(jsonPath("$.wrongQuestions[0].pointName").value("数据流图 DFD"))
                .andExpect(jsonPath("$.assignments[0].taskTitle").value("数据流图实验"))
                .andExpect(jsonPath("$.aiActivity[0].title").value("DFD 加工命名疑惑"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 严禁返回私人笔记、匿名科研评价原文或助教对话消息正文
        assertThat(body).doesNotContain("PRIVATE_STUDENT_NOTE_SECRET");
        assertThat(body).doesNotContain("SENSITIVE_EVAL_FEEDBACK");
        assertThat(body).doesNotContain("SECRET_AI_QUESTION_TEXT");
    }
}
