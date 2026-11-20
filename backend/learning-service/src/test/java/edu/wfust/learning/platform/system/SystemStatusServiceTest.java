package edu.wfust.learning.platform.system;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStatusServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void reportsDegradedWhenOptionalKnowledgeDependencyIsDownButLearningStorageIsUp() {
        var service = new SystemStatusService(Map.of(
                "mysql", () -> true,
                "redis", () -> true,
                "rabbitmq", () -> true,
                "knowledge", () -> false,
                "ai", () -> true
        ), clock);

        var status = service.current();

        assertThat(status.overallStatus()).isEqualTo("DEGRADED");
        assertThat(status.checkedAt()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
        assertThat(status.dependencies()).extracting(SystemStatusService.Dependency::key)
                .containsExactly("mysql", "redis", "rabbitmq", "knowledge", "ai");
        assertThat(status.dependencies().get(3).status()).isEqualTo("DOWN");
        assertThat(status.dependencies().get(3).impact()).contains("知识图谱");
    }

    @Test
    void reportsDownWhenRequiredLearningStorageIsDown() {
        var service = new SystemStatusService(Map.of(
                "mysql", () -> false,
                "redis", () -> true,
                "rabbitmq", () -> true,
                "knowledge", () -> true,
                "ai", () -> true
        ), clock);

        assertThat(service.current().overallStatus()).isEqualTo("DOWN");
    }
}
