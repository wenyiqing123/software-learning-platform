package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AnswerCoordinatorTest {
    @Test
    void preparationCoordinatorPreservesThePythonRankedPromptAndEvidenceMetadata() {
        var knowledge = mock(PythonKnowledgeClient.class);
        var model = mock(AiModelClient.class);
        var prompt = new PythonKnowledgeResponse("question", List.of(), List.of(new PythonTriple("加工", "命名", "动宾短语", .9)), " ORIGINAL PROMPT ", true, "template");
        when(model.settings()).thenReturn(new ModelSettings("http://provider", "key", "model", .2, 128, Duration.ofSeconds(1)));
        when(knowledge.retrieve(anyString(), anyList(), anyList())).thenReturn(prompt);

        var coordinator = new AnswerPreparationCoordinator(knowledge, new PromptStrategy(JsonMapper.builder().build()), model, null, null);
        var prepared = coordinator.prepare(new AnswerRequest("question", QaMode.RANKED), coordinator.retrieveSnapshot(new AnswerRequest("question", QaMode.RANKED), false), null);

        assertThat(prepared.prompt()).isEqualTo(" ORIGINAL PROMPT ");
        assertThat(prepared.provenance().promptSource()).isEqualTo("PYTHON_ORIGINAL");
        assertThat(prepared.knowledge().triples()).hasSize(1);
    }
}
