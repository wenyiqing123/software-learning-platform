package edu.wfust.learning.platform.experiment;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ExperimentExportTest {
    @Test
    void exportOmitsPromptBodiesAndKeepsVersionedGenerationSnapshot() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("mode", "RANKED"); result.put("answer", "加工命名使用动宾短语");
        result.put("prompt", "hidden original prompt body"); result.put("latencyMs", 123L);
        result.put("provenance", Map.of("systemPromptVersion", "ranked-grounded-v3", "systemPromptSha256", "hash", "model", "test-model", "temperature", 0.1, "maxTokens", 512));
        var run = new AiExperimentController.ExperimentView("1", "问题", "2026-11-20T00:00:00", List.of(result), Map.of(), Map.of("experimentType", "METHODS", "metricsByMode", Map.of()), null, null);
        var rows = new ExperimentExport().rows(List.of(run));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).doesNotContainKey("prompt");
        assertThat(rows.getFirst()).containsEntry("promptVersion", "ranked-grounded-v3");
        assertThat(ExperimentExport.csv(rows)).doesNotContain("hidden original prompt body");
    }
}
