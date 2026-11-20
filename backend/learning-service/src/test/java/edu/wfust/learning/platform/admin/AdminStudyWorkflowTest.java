package edu.wfust.learning.platform.admin;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import edu.wfust.learning.platform.mapper.*;
import edu.wfust.learning.platform.messaging.LearningEventPublisher;
import edu.wfust.learning.platform.quiz.QuizController;
import edu.wfust.learning.platform.quiz.QuizService;
import edu.wfust.learning.platform.course.CourseController;
import edu.wfust.learning.platform.knowledge.PrerequisitePathController;
import edu.wfust.learning.platform.knowledge.PrerequisitePathService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AdminStudyWorkflowTest.Config.class)
@WebAppConfiguration
class AdminStudyWorkflowTest {
    @Configuration @EnableWebMvc @EnableMethodSecurity @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @ComponentScan(basePackages = {"edu.wfust.learning.platform.admin", "edu.wfust.learning.platform.course.content"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Config.class))
    @MapperScan("edu.wfust.learning.platform.mapper")
    static class Config {
        @Bean DataSource dataSource() { var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:admin-study;MODE=MySQL;DB_CLOSE_DELAY=-1"); return ds; }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
            var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true); factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build(); }
        @Bean QuizService quizService(QuizQuestionMapper q, QuizAttemptMapper a, WrongQuestionMapper w, KnowledgePointMapper k, ObjectMapper json) {
            return new QuizService(q, a, w, k, json, mock(LearningEventPublisher.class));
        }
        @Bean QuizController quizController(QuizService service) { return new QuizController(service); }
        @Bean CourseController courseController(CourseMapper courses, KnowledgePointMapper points, KnowledgeMasteryMapper mastery) { return new CourseController(courses, points, mastery); }
        @Bean PrerequisitePathService prerequisitePathService(CourseMapper courses, KnowledgePointMapper points, KnowledgeMasteryMapper mastery, ObjectMapper json) {
            return new PrerequisitePathService(courses, points, mastery, json);
        }
        @Bean PrerequisitePathController prerequisitePathController(PrerequisitePathService service) { return new PrerequisitePathController(service); }
    }
    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    static final String VALID = """
        {"courseId":1,"knowledgePointId":1,"type":"SINGLE","stem":"需求评审的主要目标是什么？",
         "options":[{"key":"A","text":"发现需求问题"},{"key":"B","text":"代替所有测试"}],
         "answer":["A"],"explanation":"评审用于发现歧义和遗漏。","difficulty":2}
        """;
    @BeforeEach void setup() {
        new ResourceDatabasePopulator(new ClassPathResource("admin-study-base.sql")).execute(dataSource);
        var migration = new ClassPathResource("db/migration/V4__course_content_question_bank.sql");
        new ResourceDatabasePopulator(migration).execute(dataSource);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void studentsCannotReadOrMutateAdminCoursesOrQuestions() throws Exception {
        for (var path : new String[]{"/api/admin/courses", "/api/admin/questions", "/api/admin/knowledge-points?courseId=1", "/api/admin/courses/1/prerequisite-quality"})
            mvc.perform(get(path).with(user("1").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/questions").with(user("1").roles("STUDENT")).contentType("application/json").content(VALID)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/courses/1/enabled").with(user("1").roles("STUDENT")).contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isForbidden());
    }
    @Test void administratorCanReadPrerequisiteQualityReport() throws Exception {
        mvc.perform(get("/api/admin/courses/1/prerequisite-quality").with(user("1").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courseId").value(1))
            .andExpect(jsonPath("$.pointCount").value(8))
            .andExpect(jsonPath("$.valid").value(true));
    }
    @Test void learningPathReturnsSameCoursePrerequisitesInStudyOrderForCurrentUser() throws Exception {
        long targetId = jdbc.queryForObject("SELECT id FROM knowledge_point WHERE course_id=1 AND graph_entity=?", Long.class, "机票预定系统");
        mvc.perform(get("/api/knowledge/learning-path").param("targetId", Long.toString(targetId)).with(user("101").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetId").value(targetId))
            .andExpect(jsonPath("$.nodes[2].id").value(targetId))
            .andExpect(jsonPath("$.nodes.length()").value(3))
            .andExpect(jsonPath("$.nodes[0].status").value("UP_NEXT"))
            .andExpect(jsonPath("$.cycleDetected").value(false));
    }
    @Test void learningPathRequiresAuthenticationAndEnabledTarget() throws Exception {
        long targetId = jdbc.queryForObject("SELECT id FROM knowledge_point WHERE course_id=1 AND graph_entity=?", Long.class, "需求分析");
        mvc.perform(get("/api/knowledge/learning-path").param("targetId", Long.toString(targetId)))
            .andExpect(status().isForbidden());
        jdbc.update("UPDATE knowledge_point SET enabled=FALSE WHERE id=?", targetId);
        mvc.perform(get("/api/knowledge/learning-path").param("targetId", Long.toString(targetId)).with(user("101").roles("STUDENT")))
            .andExpect(status().isNotFound());
    }
    @Test void disabledCourseIsRemovedFromStudentPointsAndQuizPaths() throws Exception {
        jdbc.update("UPDATE course SET enabled=FALSE WHERE id=1");
        mvc.perform(get("/api/knowledge/points").with(user("1").roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/knowledge/points?courseId=1").with(user("1").roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/quizzes").with(user("1").roles("STUDENT")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        Long questionId = jdbc.queryForObject("SELECT MIN(id) FROM quiz_question WHERE source='COURSE_CONTENT_SEED'", Long.class);
        mvc.perform(post("/api/quizzes/" + questionId + "/submit").with(user("1").roles("STUDENT"))
            .contentType("application/json").content("{\"answer\":[\"A\"]}"))
            .andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings = {
        "\"type\":\"SINGLE\"|\"type\":\"ESSAY\"",
        "\"answer\":[\"A\"]|\"answer\":[\"C\"]",
        "\"answer\":[\"A\"]|\"answer\":[\"A\",\"B\"]",
        "\"answer\":[\"A\"]|\"answer\":[]",
        "\"key\":\"B\"|\"key\":\"A\"",
        "\"difficulty\":2|\"difficulty\":6",
        "\"knowledgePointId\":1|\"knowledgePointId\":9",
        "\"knowledgePointId\":1|\"knowledgePointId\":null",
        "\"text\":\"发现需求问题\"|\"text\":\" \"",
        "\"type\":\"SINGLE\"|\"type\":\"MULTIPLE\"",
        "\"type\":\"SINGLE\"|\"type\":\"TRUE_FALSE\""
    }) void rejectsInvalidQuestionBeforePersistence(String replacement) throws Exception {
        var parts = replacement.split("\\|", 2);
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM quiz_question", Long.class);
        mvc.perform(post("/api/admin/questions").with(user("1").roles("ADMIN")).contentType("application/json").content(VALID.replace(parts[0], parts[1])))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz_question", Long.class)).isEqualTo(before);
    }
    @Test void createsUpdatesAndDisablesQuestionWithoutExposingItsKeyOnStudentListing() throws Exception {
        var created = mvc.perform(post("/api/admin/questions").with(user("1").roles("ADMIN")).contentType("application/json").content(VALID))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.answer[0]").value("A")).andReturn();
        long id = JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/admin/questions/" + id).with(user("1").roles("ADMIN")).contentType("application/json").content(VALID.replace("\"answer\":[\"A\"]", "\"answer\":[\"B\"]"))).andExpect(status().isOk()).andExpect(jsonPath("$.answer[0]").value("B"));
        mvc.perform(get("/api/quizzes").with(user("1").roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].answer").doesNotExist()).andExpect(jsonPath("$[0].answerJson").doesNotExist()).andExpect(jsonPath("$[0].explanation").doesNotExist());
        mvc.perform(put("/api/admin/questions/" + id + "/enabled").with(user("1").roles("ADMIN")).contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT enabled FROM quiz_question WHERE id=?", Boolean.class, id)).isFalse();
        mvc.perform(get("/api/quizzes").param("knowledgePointId", "1").with(user("1").roles("STUDENT"))).andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }
    @Test void duplicateCourseConflictsAndDisableHidesStudy() throws Exception {
        String course = "{\"code\":\"NEW\",\"name\":\"软件测试\",\"term\":\"2026 秋季\",\"description\":\"测试课程\",\"graphSpaceId\":1}";
        mvc.perform(post("/api/admin/courses").with(user("1").roles("ADMIN")).contentType("application/json").content(course)).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/courses").with(user("1").roles("ADMIN")).contentType("application/json").content(course)).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/courses/1/enabled").with(user("1").roles("ADMIN")).contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/1/study").with(user("1").roles("STUDENT"))).andExpect(status().isNotFound());
    }
    @Test void seededContentCoversEachCurrentPointAndContainsTwentyFourValidatedQuestions() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quiz_question WHERE source='COURSE_CONTENT_SEED'", Integer.class)).isEqualTo(24);
        assertThat(jdbc.queryForList("SELECT COUNT(*) FROM quiz_question WHERE source='COURSE_CONTENT_SEED' GROUP BY knowledge_point_id", Integer.class)).containsExactlyInAnyOrder(3,3,3,3,3,3,3,3);
        mvc.perform(get("/api/courses/1/study").with(user("1").roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$.chapters.length()").value(2)).andExpect(jsonPath("$.chapters[0].sections.length()").value(5)).andExpect(jsonPath("$.completedSections").value(0));
    }
    @Test void progressUsesAuthenticatedOwnerAndCannotTargetAnotherCourse() throws Exception {
        mvc.perform(get("/api/courses/1/study").with(user("1").roles("STUDENT"))).andExpect(status().isOk());
        long section = jdbc.queryForObject("SELECT MIN(id) FROM course_study_section", Long.class);
        mvc.perform(put("/api/courses/1/study/sections/" + section + "/progress").with(user("1").roles("STUDENT")).contentType("application/json").content("{\"completed\":true,\"userId\":2}")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/1/study").with(user("1").roles("STUDENT"))).andExpect(jsonPath("$.completedSections").value(1));
        mvc.perform(get("/api/courses/1/study").with(user("2").roles("STUDENT"))).andExpect(jsonPath("$.completedSections").value(0));
        mvc.perform(put("/api/courses/2/study/sections/" + section + "/progress").with(user("1").roles("STUDENT")).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/courses/1/study/sections/" + section + "/progress").with(user("1").roles("STUDENT")).contentType("application/json").content("{\"completed\":false}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_study_progress WHERE user_id=2", Integer.class)).isZero();
        mvc.perform(get("/api/courses/1/study").with(user("1").roles("STUDENT"))).andExpect(jsonPath("$.completedSections").value(0));
    }
    @Test void allSeedPayloadsAreValidAndReadingsAreSubstantive() throws Exception {
        var json = JsonMapper.builder().build();
        for (var row : jdbc.queryForList("SELECT * FROM quiz_question WHERE source='COURSE_CONTENT_SEED'")) {
            var request = new AdminPayloads.QuestionRequest(((Number) row.get("COURSE_ID")).longValue(), ((Number) row.get("KNOWLEDGE_POINT_ID")).longValue(),
                row.get("QUESTION_TYPE").toString(), jdbc.queryForObject("SELECT stem FROM quiz_question WHERE id=?", String.class, row.get("ID")),
                json.readValue(jdbc.queryForObject("SELECT options_json FROM quiz_question WHERE id=?", String.class, row.get("ID")), new tools.jackson.core.type.TypeReference<java.util.List<AdminPayloads.Option>>() { }),
                json.readValue(jdbc.queryForObject("SELECT answer_json FROM quiz_question WHERE id=?", String.class, row.get("ID")), new tools.jackson.core.type.TypeReference<java.util.List<String>>() { }),
                jdbc.queryForObject("SELECT explanation FROM quiz_question WHERE id=?", String.class, row.get("ID")), ((Number) row.get("DIFFICULTY")).intValue());
            QuestionPayloadValidator.validate(request);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_study_section", Integer.class)).isEqualTo(8);
        for (String content : jdbc.queryForList("SELECT content_json FROM course_study_section", String.class)) {
            var blocks = json.readTree(content);
            assertThat(blocks.size()).isGreaterThanOrEqualTo(3);
            assertThat(content.length()).isGreaterThan(400);
        }
    }
    @Test void prerequisiteArraysContainNumericIdsResolvedWithinTheSameCourse() {
        var json = JsonMapper.builder().build();
        var expected = java.util.Map.of("数据流图DFD", java.util.List.of("需求分析"), "用例图", java.util.List.of("需求分析"),
            "软件需求规格说明书", java.util.List.of("需求分析"), "数据存储", java.util.List.of("数据流图DFD"),
            "软件维护", java.util.List.of("软件生命周期"), "机票预定系统", java.util.List.of("需求分析", "数据流图DFD"));
        expected.forEach((entity, dependencies) -> {
            var ids = json.readTree(jdbc.queryForObject("SELECT prerequisites_json FROM knowledge_point WHERE course_id=1 AND graph_entity=?", String.class, entity));
            assertThat(ids.isArray()).isTrue(); assertThat(ids.size()).isEqualTo(dependencies.size());
            for (int i = 0; i < ids.size(); i++) {
                assertThat(ids.get(i).isNumber()).isTrue();
                assertThat(ids.get(i).asLong()).isEqualTo(jdbc.queryForObject("SELECT id FROM knowledge_point WHERE course_id=1 AND graph_entity=?", Long.class, dependencies.get(i)));
            }
        });
        assertThat(jdbc.queryForObject("SELECT prerequisites_json FROM knowledge_point WHERE id=9", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT graph_space_id FROM course WHERE id=1", Integer.class)).isEqualTo(1);
    }
    @Test void courseGraphMappingIsRestrictedToExistingSpaceOne() throws Exception {
        mvc.perform(post("/api/admin/courses").with(user("1").roles("ADMIN")).contentType("application/json").content("""
            {"code":"SPACE","name":"课程","term":"2026 秋季","description":"说明","graphSpaceId":2}
            """)).andExpect(status().isBadRequest());
    }
    @Test void trueFalseAndMultiplePayloadsPersistTheirKeys() throws Exception {
        mvc.perform(post("/api/admin/questions").with(user("1").roles("ADMIN")).contentType("application/json").content("""
            {"courseId":1,"knowledgePointId":1,"type":"TRUE_FALSE","stem":"需求应可验证。",
             "options":[{"key":"TRUE","text":"正确"},{"key":"FALSE","text":"错误"}],"answer":["TRUE"],"explanation":"需求应有验收条件。","difficulty":1}
            """)).andExpect(status().isCreated()).andExpect(jsonPath("$.answer[0]").value("TRUE"));
        mvc.perform(post("/api/admin/questions").with(user("1").roles("ADMIN")).contentType("application/json").content(VALID.replace("\"type\":\"SINGLE\"", "\"type\":\"MULTIPLE\"").replace("\"answer\":[\"A\"]", "\"answer\":[\"A\",\"B\"]")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.answer.length()").value(2));
    }
    @Test void invalidUpdateCannotChangeExistingQuestionAndDisabledPointCannotBePublished() throws Exception {
        long id = jdbc.queryForObject("SELECT MIN(id) FROM quiz_question", Long.class);
        String oldStem = jdbc.queryForObject("SELECT stem FROM quiz_question WHERE id=?", String.class, id);
        mvc.perform(put("/api/admin/questions/" + id).with(user("1").roles("ADMIN")).contentType("application/json").content(VALID.replace("\"knowledgePointId\":1", "\"knowledgePointId\":9"))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT stem FROM quiz_question WHERE id=?", String.class, id)).isEqualTo(oldStem);
        jdbc.update("UPDATE knowledge_point SET enabled=FALSE WHERE id=1");
        mvc.perform(post("/api/admin/questions").with(user("1").roles("ADMIN")).contentType("application/json").content(VALID)).andExpect(status().isBadRequest());
    }

}
