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
}
