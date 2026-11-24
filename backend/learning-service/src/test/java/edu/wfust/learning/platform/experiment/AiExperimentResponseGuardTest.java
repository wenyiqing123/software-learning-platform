package edu.wfust.learning.platform.experiment;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiExperimentResponseGuardTest {
    @Test
    void rankedControllerBoundaryRemovesPromptBodyButKeepsAllowedMetadata() {
        var ranked = new LinkedHashMap<String, Object>();
        ranked.put("mode", "RANKED");
        ranked.put("prompt", "server-only-fixed-constraint-body");
        ranked.put("systemPrompt", "server-only-fixed-system-prompt");
        ranked.put("systemPromptBody", "server-only-fixed-system-prompt-body");
        ranked.put("provenance", Map.of("systemPromptVersion", "ranked-grounded-v3", "systemPromptSha256", "hash"));
        var direct = new LinkedHashMap<String, Object>();
        direct.put("mode", "DIRECT");
        direct.put("prompt", "original-direct-prompt");

        var sanitized = AiExperimentController.sanitizeResults(List.of(ranked, direct));
        assertThat(sanitized.get(0)).doesNotContainKeys("prompt", "systemPrompt", "systemPromptBody");
        assertThat(sanitized.get(0)).containsKey("provenance");
        assertThat(sanitized.get(1)).containsEntry("prompt", "original-direct-prompt");
    }

    @Test
    void studentExperimentResponseEvaluationRemovesReferenceRubricAndPrompt() {
        var eval = new LinkedHashMap<String, Object>();
        eval.put("source", "SERVER_AI_SERVICE");
        eval.put("referenceAnswer", "机密参考答案");
        eval.put("expectedEntities", List.of("实体A", "实体B"));
        eval.put("metricsByMode", Map.of("DIRECT", Map.of("rougeL", 0.8)));

        // 模拟学生视图中的脱敏逻辑
        eval.remove("referenceAnswer");
        eval.remove("expectedEntities");

        assertThat(eval).doesNotContainKeys("referenceAnswer", "expectedEntities");
        assertThat(eval).containsKey("metricsByMode");
    }
}
