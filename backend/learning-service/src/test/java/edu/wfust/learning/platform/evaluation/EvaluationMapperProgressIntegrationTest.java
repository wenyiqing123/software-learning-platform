package edu.wfust.learning.platform.evaluation;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import edu.wfust.learning.platform.mapper.EvaluationMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(EvaluationMapperProgressIntegrationTest.Config.class)
class EvaluationMapperProgressIntegrationTest {

    @Configuration(proxyBeanMethods = false)
    @MapperScan(basePackageClasses = EvaluationMapper.class, annotationClass = org.apache.ibatis.annotations.Mapper.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:evaluation-progress-test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    private DataSource dataSource;

    @Autowired
    private EvaluationMapper mapper;

    @BeforeEach
    void setupSchemaAndData() {
        String sql = """
            DROP ALL OBJECTS;
            CREATE TABLE sys_user (
                id BIGINT PRIMARY KEY,
                username VARCHAR(64),
                role VARCHAR(32)
            );
            CREATE TABLE learning_evaluation_session (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                condition_key VARCHAR(80),
                task_key VARCHAR(120),
                task_order VARCHAR(16),
                assignment_id BIGINT,
                consent_version VARCHAR(32),
                consented_at TIMESTAMP,
                started_at TIMESTAMP,
                completed_at TIMESTAMP,
                elapsed_seconds INT,
                psychological_effort INT,
                feedback VARCHAR(2000),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE learning_evaluation_measurement (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                session_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                phase VARCHAR(32) NOT NULL,
                score DOUBLE NOT NULL,
                responses_json TEXT,
                measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY uk_session_phase (session_id, phase)
            );

            -- 插入两个学生用户
            INSERT INTO sys_user VALUES (101, 'student101', 'STUDENT');
            INSERT INTO sys_user VALUES (102, 'student102', 'STUDENT');

            -- User 101 的 session 1
            INSERT INTO learning_evaluation_session(id, user_id, condition_key, task_key, task_order, assignment_id)
            VALUES (1, 101, 'METHODS', 'TASK-1', 'A→B', 11);

            -- User 101 完成了 PRE、POST、DELAYED 三个阶段
            INSERT INTO learning_evaluation_measurement(session_id, user_id, phase, score) VALUES (1, 101, 'PRE', 75.0);
            INSERT INTO learning_evaluation_measurement(session_id, user_id, phase, score) VALUES (1, 101, 'POST', 88.0);
            INSERT INTO learning_evaluation_measurement(session_id, user_id, phase, score) VALUES (1, 101, 'DELAYED', 92.0);

            -- User 102 的 session 2
            INSERT INTO learning_evaluation_session(id, user_id, condition_key, task_key, task_order, assignment_id)
            VALUES (2, 102, 'METHODS', 'TASK-1', 'B→A', 12);

            -- User 102 只完成了 PRE
            INSERT INTO learning_evaluation_measurement(session_id, user_id, phase, score) VALUES (2, 102, 'PRE', 60.0);
            """;
        new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)))
                .execute(dataSource);
    }

    @Test
    void listOwnedPhaseStatusesReturnsOnlyOwnerRowsAndAggregatesThreePhases() {
        // user 101 应精确返回 session 1 的三个 phase
        List<EvaluationMapper.PhaseRow> rows101 = mapper.listOwnedPhaseStatuses(101L);
        assertThat(rows101).hasSize(3);
        assertThat(rows101).extracting(EvaluationMapper.PhaseRow::phase)
                .containsExactlyInAnyOrder("PRE", "POST", "DELAYED");
        assertThat(rows101).allSatisfy(r -> assertThat(r.sessionId()).isEqualTo(1L));

        // user 102 只应返回自己的 session 2 的 1 个 phase，绝不泄露 user 101 的数据
        List<EvaluationMapper.PhaseRow> rows102 = mapper.listOwnedPhaseStatuses(102L);
        assertThat(rows102).hasSize(1);
        assertThat(rows102.get(0).sessionId()).isEqualTo(2L);
        assertThat(rows102.get(0).phase()).isEqualTo("PRE");

        // 不存在的用户应返回空列表
        List<EvaluationMapper.PhaseRow> rowsEmpty = mapper.listOwnedPhaseStatuses(999L);
        assertThat(rowsEmpty).isEmpty();
    }

    @Test
    void hasMeasurementEnforcesOwnerBoundaryAndPhaseMatching() {
        // user 101 拥有 session 1 的 PRE、POST、DELAYED
        assertThat(mapper.hasMeasurement(101L, 1L, "PRE")).isTrue();
        assertThat(mapper.hasMeasurement(101L, 1L, "POST")).isTrue();
        assertThat(mapper.hasMeasurement(101L, 1L, "DELAYED")).isTrue();
        assertThat(mapper.hasMeasurement(101L, 1L, "EQUIVALENT")).isFalse();

        // 跨用户检查：user 102 访问 session 1 必须返回 false
        assertThat(mapper.hasMeasurement(102L, 1L, "PRE")).isFalse();
        assertThat(mapper.hasMeasurement(102L, 1L, "POST")).isFalse();

        // user 101 访问 user 102 的 session 2 必须返回 false
        assertThat(mapper.hasMeasurement(101L, 2L, "PRE")).isFalse();
    }
}
