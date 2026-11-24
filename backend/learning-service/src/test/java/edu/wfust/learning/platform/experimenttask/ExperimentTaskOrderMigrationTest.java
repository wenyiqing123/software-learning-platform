package edu.wfust.learning.platform.experimenttask;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentTaskOrderMigrationTest {

    @Test
    void migrationFromV14PreservesExistingGroupsWithNullOrderAndEnablesReadWrite() throws Exception {
        String dbName = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // 建立 V14 阶段初始 schema（未执行 V15/V16）
            stmt.execute("CREATE TABLE sys_user (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(60) NOT NULL, " +
                    "password_hash VARCHAR(100) NOT NULL, " +
                    "display_name VARCHAR(80) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL, " +
                    "enabled BOOLEAN NOT NULL DEFAULT TRUE" +
                    ")");

            stmt.execute("CREATE TABLE experiment_task (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "title VARCHAR(160) NOT NULL, " +
                    "description VARCHAR(1000) NOT NULL DEFAULT '', " +
                    "experiment_type VARCHAR(20) NOT NULL DEFAULT 'METHODS', " +
                    "question VARCHAR(2000) NOT NULL, " +
                    "reference_answer VARCHAR(4000) NOT NULL DEFAULT '', " +
                    "expected_entities_json JSON NOT NULL, " +
                    "include_ablations BOOLEAN NOT NULL DEFAULT FALSE, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', " +
                    "created_by BIGINT NOT NULL, " +
                    "created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
                    "starts_at TIMESTAMP(6) NULL, " +
                    "ends_at TIMESTAMP(6) NULL" +
                    ")");

            stmt.execute("CREATE TABLE experiment_group (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "task_id BIGINT NOT NULL, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "condition_key VARCHAR(80) NOT NULL, " +
                    "sort_order INT NOT NULL DEFAULT 0, " +
                    "UNIQUE KEY uk_experiment_group_task_condition (task_id, condition_key)" +
                    ")");

            stmt.execute("CREATE TABLE experiment_assignment (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "task_id BIGINT NOT NULL, " +
                    "group_id BIGINT NOT NULL, " +
                    "user_id BIGINT NOT NULL, " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED', " +
                    "assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
                    "started_at TIMESTAMP(6) NULL, " +
                    "completed_at TIMESTAMP(6) NULL" +
                    ")");

            stmt.execute("CREATE TABLE ai_experiment_run (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "user_id BIGINT NOT NULL, " +
                    "task_id BIGINT NULL, " +
                    "assignment_id BIGINT NULL" +
                    ")");

            stmt.execute("CREATE TABLE learning_evaluation_session (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "user_id BIGINT NOT NULL, " +
                    "condition_key VARCHAR(60) NOT NULL, " +
                    "task_key VARCHAR(120) NOT NULL, " +
                    "consent_version VARCHAR(30) NOT NULL, " +
                    "consented_at TIMESTAMP(6) NOT NULL, " +
                    "started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)" +
                    ")");

            // 写入旧数据：V14 阶段创建的实验组（此时无 task_order 字段）
            stmt.execute("INSERT INTO sys_user (id, username, password_hash, display_name, role) " +
                    "VALUES (1, 'admin', 'unused', '管理员', 'ADMIN')");
            stmt.execute("INSERT INTO experiment_task (id, title, question, expected_entities_json, created_by) " +
                    "VALUES (10, '基准测试任务', '测试题目内容', '[]', 1)");
            stmt.execute("INSERT INTO experiment_group (id, task_id, name, condition_key, sort_order) " +
                    "VALUES (100, 10, '历史实验组A', 'CONDITION_A', 1)");
        }

        // 准备 Flyway 迁移脚本：读取实际 migration 文件，适配 H2 兼容执行
        Path migrationDir = Files.createTempDirectory("flyway-migration-test");
        try {
            // V15: 保持原有 DDL 语义，转为 H2 MySQL 兼容分号拆分
            String v15Content = "ALTER TABLE learning_evaluation_session ADD COLUMN task_order VARCHAR(120) NULL;\n" +
                    "ALTER TABLE learning_evaluation_session ADD COLUMN assignment_id BIGINT NULL;\n" +
                    "ALTER TABLE learning_evaluation_session ADD KEY idx_eval_session_assignment (assignment_id);\n";
            Files.writeString(migrationDir.resolve("V15__evaluation_order_and_delayed_measurement.sql"), v15Content, StandardCharsets.UTF_8);

            // V16: 从生产 migration 资源原样读取并写入
            try (InputStream in = getClass().getResourceAsStream("/db/migration/V16__experiment_group_task_order.sql")) {
                assertThat(in).as("V16 migration script must exist on classpath").isNotNull();
                String v16Content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(migrationDir.resolve("V16__experiment_group_task_order.sql"), v16Content, StandardCharsets.UTF_8);
            }

            // 配置 Flyway 以 V14 为基线，执行 V15 和 V16 迁移
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace("\\", "/"))
                    .baselineVersion("14")
                    .baselineOnMigrate(true)
                    .load();

            int applied = flyway.migrate().migrationsExecuted;
            assertThat(applied).isEqualTo(2); // V15, V16

            // 断言迁移后：
            // 1. 旧实验组保留且 task_order 列值保持为 NULL
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id, name, task_order FROM experiment_group WHERE id = 100")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("id")).isEqualTo(100L);
                    assertThat(rs.getString("name")).isEqualTo("历史实验组A");
                    assertThat(rs.getString("task_order")).isNull();
                }

                // 2. 新列正常支持 A→B 写入与读取
                stmt.execute("UPDATE experiment_group SET task_order = 'A→B' WHERE id = 100");
                try (ResultSet rs = stmt.executeQuery("SELECT task_order FROM experiment_group WHERE id = 100")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("task_order")).isEqualTo("A→B");
                }

                // 3. 能够插入包含 B→A 顺序的新实验组
                stmt.execute("INSERT INTO experiment_group (id, task_id, name, condition_key, sort_order, task_order) " +
                        "VALUES (101, 10, '新实验组B', 'CONDITION_B', 2, 'B→A')");
                try (ResultSet rs = stmt.executeQuery("SELECT task_order FROM experiment_group WHERE id = 101")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("task_order")).isEqualTo("B→A");
                }
            }
        } finally {
            // 清理临时文件
            try (var s = Files.walk(migrationDir)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }
}
