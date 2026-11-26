package edu.wfust.learning.platform.flashcard;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import edu.wfust.learning.platform.mapper.FlashcardMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(FlashcardServiceTest.Config.class)
class FlashcardServiceTest {
    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = FlashcardMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:flashcard-service;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            var config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            return factory.getObject();
        }
    }

    @Autowired
    DataSource dataSource;
    @Autowired
    FlashcardMapper mapper;

    private FlashcardService service;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setUp() {
        String sql = """
            DROP ALL OBJECTS;
            CREATE TABLE sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(60));
            CREATE TABLE course (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120), enabled BOOLEAN);
            CREATE TABLE course_chapter (id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id BIGINT, name VARCHAR(120));
            CREATE TABLE knowledge_point (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                course_id BIGINT,
                chapter_id BIGINT,
                name VARCHAR(160),
                graph_entity VARCHAR(200),
                description VARCHAR(800),
                sort_order INT,
                enabled BOOLEAN
            );
            CREATE TABLE course_study_section (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                course_id BIGINT,
                knowledge_point_id BIGINT,
                title VARCHAR(160),
                objectives_json CLOB
            );
            CREATE TABLE knowledge_mastery (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT,
                knowledge_point_id BIGINT,
                mastery_score DECIMAL(6,2)
            );
            CREATE TABLE flashcard_review_state (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                knowledge_point_id BIGINT NOT NULL,
                recall_rating TINYINT NOT NULL DEFAULT 2,
                review_count INT NOT NULL DEFAULT 0,
                interval_minutes INT NOT NULL DEFAULT 1440,
                next_due_at TIMESTAMP(6) NOT NULL,
                last_reviewed_at TIMESTAMP(6) NULL,
                created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
            );
            INSERT INTO sys_user VALUES (1, 'student1'), (2, 'student2');
            INSERT INTO course VALUES (10, '软件工程', TRUE), (11, '停用课程', FALSE);
            INSERT INTO course_chapter VALUES (100, 10, '需求工程');
            INSERT INTO knowledge_point VALUES
                (1001, 10, 100, '需求分析', '需求分析', '梳理系统边界与目标', 1, TRUE),
                (1002, 10, 100, '数据流图', '数据流图DFD', '建模业务数据流动', 2, TRUE),
                (1003, 11, NULL, '停用知识点', 'OLD', '已停用', 1, FALSE);
            INSERT INTO course_study_section VALUES (201, 10, 1001, '1.1 需求边界分析', '["识别参与者","写出可观察验收条件"]');
            INSERT INTO knowledge_mastery VALUES (1, 1, 1001, 80.00);
            """;
        new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))).execute(dataSource);
        service = new FlashcardService(mapper, JsonMapper.builder().build(), fixedClock);
    }

    @Test
    void listsEnabledCardsAndPersistsUserScopedReviewWithFixedIntervals() {
        var initial = service.list(1L, 10L, false);
        assertThat(initial).hasSize(2);
        assertThat(initial.getFirst().name()).isEqualTo("需求分析");
        assertThat(initial.getFirst().due()).isTrue();
        assertThat(initial.getFirst().objectives()).containsExactly("识别参与者", "写出可观察验收条件");
        assertThat(initial.getFirst().masteryScore()).isEqualTo(80);

        // rating=3 (轻松 -> 10080 分钟 / 7 天)
        var afterEasy = service.review(1L, 1001L, 3);
        assertThat(afterEasy.intervalMinutes()).isEqualTo(10_080);
        assertThat(afterEasy.reviewCount()).isEqualTo(1);
        assertThat(afterEasy.due()).isFalse();

        // 只筛选当前到期卡片时，1001 已被排到 7 天后，仅剩下 1002；同时另一位用户 2 不受影响
        var dueUser1 = service.list(1L, null, true);
        assertThat(dueUser1).extracting(FlashcardService.FlashcardView::knowledgePointId).containsExactly(1002L);
        var dueUser2 = service.list(2L, null, true);
        assertThat(dueUser2).extracting(FlashcardService.FlashcardView::knowledgePointId).containsExactly(1001L, 1002L);

        // rating=0 (忘记 -> 10 分钟) 更新同一条状态并累加 reviewCount
        var afterForgot = service.review(1L, 1001L, 0);
        assertThat(afterForgot.intervalMinutes()).isEqualTo(10);
        assertThat(afterForgot.reviewCount()).isEqualTo(2);
    }

    @Test
    void rejectsDisabledKnowledgePointOrOutOfRangeRating() {
        assertThatThrownBy(() -> service.review(1L, 1003L, 2))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("知识点不存在或已停用");

        assertThatThrownBy(() -> service.review(1L, 1001L, 5))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("回忆评分必须在 0 到 3 之间");
    }
}
