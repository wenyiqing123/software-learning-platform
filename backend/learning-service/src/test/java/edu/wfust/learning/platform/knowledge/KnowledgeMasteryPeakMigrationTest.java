package edu.wfust.learning.platform.knowledge;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeMasteryPeakMigrationTest {

    @Test
    void migrationFromV16BackfillsPeakMasteryAndAllowsDefaultedWrites() throws Exception {
        String dbName = "mastery_peak_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE sys_user (
                  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  username VARCHAR(60) NOT NULL
                )""");
            stmt.execute("""
                CREATE TABLE knowledge_point (
                  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(160) NOT NULL
                )""");
            stmt.execute("""
                CREATE TABLE knowledge_mastery (
                  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  knowledge_point_id BIGINT NOT NULL,
                  mastery_score DECIMAL(5,2) NOT NULL DEFAULT 0,
                  attempt_count INT NOT NULL DEFAULT 0,
                  correct_count INT NOT NULL DEFAULT 0,
                  last_studied_at TIMESTAMP(6) NULL,
                  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                  UNIQUE KEY uk_mastery_user_point (user_id, knowledge_point_id)
                )""");

            stmt.execute("INSERT INTO sys_user (id, username) VALUES (1, 'student1')");
            stmt.execute("INSERT INTO knowledge_point (id, name) VALUES (10, '需求分析'), (11, '数据流图'), (12, '用例图')");
            stmt.execute("""
                INSERT INTO knowledge_mastery (id, user_id, knowledge_point_id, mastery_score, attempt_count, correct_count, last_studied_at)
                VALUES (100, 1, 10, 85.50, 6, 5, CURRENT_TIMESTAMP(6)),
                       (101, 1, 11, 0.00, 2, 0, CURRENT_TIMESTAMP(6))
                """);
        }

        Path migrationDir = Files.createTempDirectory("flyway-v17-test");
        try {
            try (InputStream in = getClass().getResourceAsStream("/db/migration/V17__knowledge_mastery_peak.sql")) {
                assertThat(in).as("V17 migration script must exist on classpath").isNotNull();
                String v17Content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(migrationDir.resolve("V17__knowledge_mastery_peak.sql"), v17Content, StandardCharsets.UTF_8);
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace("\\", "/"))
                    .baselineVersion("16")
                    .baselineOnMigrate(true)
                    .load();

            int applied = flyway.migrate().migrationsExecuted;
            assertThat(applied).isEqualTo(1);

            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id, mastery_score, peak_mastery FROM knowledge_mastery ORDER BY id")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("id")).isEqualTo(100L);
                    assertThat(rs.getBigDecimal("mastery_score")).isEqualByComparingTo("85.50");
                    assertThat(rs.getBigDecimal("peak_mastery")).isEqualByComparingTo("85.50");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("id")).isEqualTo(101L);
                    assertThat(rs.getBigDecimal("mastery_score")).isEqualByComparingTo("0.00");
                    assertThat(rs.getBigDecimal("peak_mastery")).isEqualByComparingTo("0.00");
                }

                // 不显式传 peak_mastery 的旧式插入应使用 DEFAULT 0.00
                stmt.execute("INSERT INTO knowledge_mastery (user_id, knowledge_point_id, mastery_score, attempt_count, correct_count) " +
                        "VALUES (1, 12, 66.67, 3, 2)");
                try (ResultSet rs = stmt.executeQuery("SELECT peak_mastery FROM knowledge_mastery WHERE user_id = 1 AND knowledge_point_id = 12")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal("peak_mastery")).isEqualByComparingTo(new BigDecimal("0.00"));
                }
            }
        } finally {
            try (var stream = Files.walk(migrationDir)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }
}
