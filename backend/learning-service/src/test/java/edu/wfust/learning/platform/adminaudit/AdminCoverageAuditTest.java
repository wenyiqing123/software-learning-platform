package edu.wfust.learning.platform.adminaudit;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import edu.wfust.learning.platform.admin.AdminCoverageAuditController;
import edu.wfust.learning.platform.admin.KnowledgeCoverageAuditService;
import edu.wfust.learning.platform.config.ApiExceptionHandler;
import edu.wfust.learning.platform.mapper.AdminContentAuditMapper;
import edu.wfust.learning.platform.mapper.CourseMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(AdminCoverageAuditTest.Config.class)
@WebAppConfiguration
class AdminCoverageAuditTest {

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    @EnableWebSecurity
    @MapperScan("edu.wfust.learning.platform.mapper")
    static class Config {
        @Bean(destroyMethod = "destroy")
        DataSource dataSource() {
            // Keep the DDL session open across H2 CHECK constraint evaluations in H2 2.4.x.
            return new SingleConnectionDataSource(
                "jdbc:h2:mem:admin-coverage-audit;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                true
            );
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
        KnowledgeCoverageAuditService coverageAuditService(CourseMapper courses, AdminContentAuditMapper auditMapper) {
            return new KnowledgeCoverageAuditService(courses, auditMapper);
        }

        @Bean
        AdminCoverageAuditController adminCoverageAuditController(KnowledgeCoverageAuditService service) {
            return new AdminCoverageAuditController(service);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }

        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .build();
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        new ResourceDatabasePopulator(
            new ClassPathResource("admin-study-base.sql"),
            new ClassPathResource("db/migration/V4__course_content_question_bank.sql"),
            new ClassPathResource("db/migration/V8__quiz_option_diagnosis.sql"),
            new ClassPathResource("db/migration/V13__micro_lesson_drafts.sql"),
            new ClassPathResource("db/migration/V18__flashcard_review_state.sql")
        ).execute(dataSource);
        jdbc.update("INSERT INTO sys_user (id, username, password_hash, display_name, role, enabled) VALUES (3, 'admin-user', 'unused', '管理员', 'ADMIN', TRUE)");
        jdbc.update("INSERT INTO course (id, code, name, term, description, graph_space_id, enabled) VALUES (3, 'EMPTY-COURSE', '空数据课程', '2026 秋季', '无知识点', 1, TRUE)");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rejectsAnonymousWith401AndStudentWith403() throws Exception {
        mvc.perform(get("/api/admin/content/coverage-audit"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/content/coverage-audit").with(user("1").roles("STUDENT")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/content/coverage-audit").param("courseId", "1").with(user("1").roles("STUDENT")))
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForUnknownCourseAndEmptySummaryForEmptyCourse() throws Exception {
        mvc.perform(get("/api/admin/content/coverage-audit").param("courseId", "999").with(user("3").roles("ADMIN")))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/admin/content/coverage-audit").param("courseId", "3").with(user("3").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courseId").value(3))
            .andExpect(jsonPath("$.courseName").value("空数据课程"))
            .andExpect(jsonPath("$.summary.totalPoints").value(0))
            .andExpect(jsonPath("$.summary.fullyCoveredPoints").value(0))
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void aggregatesRealAssetsAndFlagsDeterministicGapsAcrossV18Tables() throws Exception {
        Long q1 = jdbc.queryForObject("SELECT MIN(id) FROM quiz_question WHERE knowledge_point_id = 1 AND enabled = TRUE", Long.class);
        Long s1 = jdbc.queryForObject("SELECT id FROM course_study_section WHERE knowledge_point_id = 1", Long.class);
        jdbc.update(
            "INSERT INTO quiz_option_diagnosis (question_id, option_key, knowledge_point_id, rationale, enabled) VALUES (?, 'B', 1, '混淆边界概念', TRUE)",
            q1
        );
        jdbc.update(
            "INSERT INTO micro_lesson_draft (course_id, section_id, title, status, summary, objectives_json, triples_json, common_errors_json, self_test_json, prerequisites_json, generated_text, evidence_json, created_by) VALUES (1, ?, '需求分析微课', 'PUBLISHED', '摘要', '[]', '[]', '[]', '[]', '[]', '正文', '{}', 3)",
            s1
        );
        jdbc.update(
            "INSERT INTO flashcard_review_state (user_id, knowledge_point_id, recall_rating, review_count, interval_minutes, next_due_at) VALUES (1, 1, 3, 2, 10080, CURRENT_TIMESTAMP)"
        );

        Long s2 = jdbc.queryForObject("SELECT id FROM course_study_section WHERE knowledge_point_id = 2", Long.class);
        jdbc.update(
            "INSERT INTO micro_lesson_draft (course_id, section_id, title, status, summary, objectives_json, triples_json, common_errors_json, self_test_json, prerequisites_json, generated_text, evidence_json, created_by) VALUES (1, ?, '数据流图草稿', 'DRAFT', '摘要', '[]', '[]', '[]', '[]', '[]', '草稿正文', '{}', 3)",
            s2
        );

        jdbc.update(
            "INSERT INTO knowledge_point (id, course_id, chapter_id, name, graph_entity, description, sort_order, enabled) VALUES (10, 1, 1, '新增待补录概念', '新增待补录概念', '', 9, TRUE)"
        );

        mvc.perform(get("/api/admin/content/coverage-audit").param("courseId", "1").with(user("3").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.totalPoints").value(9))
            .andExpect(jsonPath("$.summary.fullyCoveredPoints").value(1))
            .andExpect(jsonPath("$.items[0].knowledgePointId").value(1))
            .andExpect(jsonPath("$.items[0].diagnosisCount").value(1))
            .andExpect(jsonPath("$.items[0].publishedMicroLessonCount").value(1))
            .andExpect(jsonPath("$.items[0].flashcardReviewCount").value(1))
            .andExpect(jsonPath("$.items[0].flashcardReady").value(true))
            .andExpect(jsonPath("$.items[0].gaps", hasSize(0)))
            .andExpect(jsonPath("$.items[1].publishedMicroLessonCount").value(0))
            .andExpect(jsonPath("$.items[1].draftMicroLessonCount").value(1))
            .andExpect(jsonPath("$.items[1].gaps[*].code", hasItem("NO_MICRO_LESSON")))
            .andExpect(jsonPath("$.items[1].gaps[*].code", hasItem("NO_DIAGNOSIS")))
            .andExpect(jsonPath("$.items[1].gaps[*].code", not(hasItem("NO_FLASHCARD"))))
            .andExpect(jsonPath("$.items[5].knowledgePointId").value(10))
            .andExpect(jsonPath("$.items[5].flashcardReady").value(false))
            .andExpect(jsonPath("$.items[5].gaps[*].code", hasItem("NO_QUESTIONS")))
            .andExpect(jsonPath("$.items[5].gaps[*].code", hasItem("NO_DIAGNOSIS")))
            .andExpect(jsonPath("$.items[5].gaps[*].code", hasItem("NO_MICRO_LESSON")))
            .andExpect(jsonPath("$.items[5].gaps[*].code", hasItem("NO_FLASHCARD")))
            .andExpect(jsonPath("$.items[8].knowledgePointId").value(8));
    }
}
