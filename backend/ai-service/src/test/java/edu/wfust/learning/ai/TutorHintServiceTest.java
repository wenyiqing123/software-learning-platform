package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TutorHintServiceTest {
    private final PythonKnowledgeClient knowledge = mock(PythonKnowledgeClient.class);
    private final AiModelClient model = mock(AiModelClient.class);
    private final TutorHintService service = new TutorHintService(knowledge, model);
    private final PythonKnowledgeResponse evidence = new PythonKnowledgeResponse("问题", List.of(),
            List.of(new PythonTriple("数据流图", "包含", "加工", .9), new PythonTriple("加工", "命名", "动宾短语", .8)),
            "prompt", true, "template");

    @BeforeEach void setup() { when(knowledge.retrieve(anyString(), anyList(), anyList(), anyList())).thenReturn(evidence); }

    @Test void levelOneReturnsThinkingDirectionWithoutCallingModel() {
        var result = service.hint(new TutorHintRequest("加工如何命名", 1));
        assertThat(result.level()).isEqualTo(1);
        assertThat(result.content()).contains("思考").doesNotContain("动宾短语");
        assertThat(result.evidence()).hasSize(2);
        verifyNoInteractions(model);
    }

    @Test void levelTwoReturnsCourseEvidenceButNotGeneratedAnswer() {
        var result = service.hint(new TutorHintRequest("加工如何命名", 2));
        assertThat(result.content()).contains("加工").contains("命名").contains("动宾短语");
        assertThat(result.nextLevelAvailable()).isTrue();
        verifyNoInteractions(model);
    }

    @Test void levelThreeUsesGroundedModelOnlyAfterEvidenceExists() {
        when(model.call(anyString(), anyString())).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("加工的命名规则应使用动宾短语描述处理逻辑。")))));
        var result = service.hint(new TutorHintRequest("加工如何命名", 3));
        assertThat(result.content()).contains("动宾短语");
        assertThat(result.source()).isEqualTo("GRAPH_GROUNDED_MODEL");
        verify(model).call(contains("数据流图"), contains("只根据给出的课程证据"));
    }

    @Test void noEvidenceNeverCallsModelAndDisclosesInsufficientEvidence() {
        when(knowledge.retrieve(anyString(), anyList(), anyList(), anyList()))
                .thenReturn(new PythonKnowledgeResponse("问题", List.of(), List.of(), "", false, "template"));
        var result = service.hint(new TutorHintRequest("没有证据的问题", 3));
        assertThat(result.hasEvidence()).isFalse();
        assertThat(result.nextLevelAvailable()).isFalse();
        assertThat(result.content()).contains("没有检索到足够证据");
        verifyNoInteractions(model);
    }
}
