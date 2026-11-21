package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Regression guards for the thesis boundary: failed evidence is never presented as a valid answer. */
class ComplianceGuardTest {
    @Test
    void graphEvidenceFailureStopsBeforeAnyModelAnswer() {
        ChatModel model = mock(ChatModel.class);
        var service = new GroundedRankedAnswer(new AiModelClient(new ModelSettings(
                "https://provider.example/v1", "test-key", "fixed-model", .2, 128, Duration.ofSeconds(2)), model),
                JsonMapper.builder().build());

        var result = service.execute("问题", new PythonKnowledgeResponse(
                "问题", List.of(), List.of(), "原始提示", false, "template"), "原始提示");

        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().code()).isEqualTo("KNOWLEDGE_INSUFFICIENT");
        assertThat(result.answer()).isBlank();
        verifyNoInteractions(model);
    }

    @Test
    void groundedTraceContainsVersionedHashesButNeverFixedPromptBody() throws Exception {
        ChatModel model = mock(ChatModel.class);
        var service = new GroundedRankedAnswer(new AiModelClient(new ModelSettings(
                "https://provider.example/v1", "test-key", "fixed-model", .2, 128, Duration.ofSeconds(2)), model),
                JsonMapper.builder().build());
        var result = service.execute("问题", new PythonKnowledgeResponse(
                "问题", List.of(), List.of(), "原始提示", false, "template"), "原始提示");
        String trace = JsonMapper.builder().build().writeValueAsString(result.trace());

        assertThat(trace).contains("selectionSystemPromptSha256");
        assertThat(trace).doesNotContain("[EVIDENCE_SELECTION_V1]");
        assertThat(trace).doesNotContain("[GROUNDED_ANSWER_V3]");
        assertThat(trace).doesNotContain("每条答案必须明确包含对应三元组");
    }
}
