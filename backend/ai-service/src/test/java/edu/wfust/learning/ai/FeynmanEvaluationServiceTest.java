package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.KnowledgeServiceUnavailableException;
import edu.wfust.learning.ai.client.PythonEntity;
import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeynmanEvaluationServiceTest {
    private final JsonMapper json = JsonMapper.builder().build();

    private static AiModelClient modelReturning(String text) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        ModelSettings settings = new ModelSettings(
                "https://provider.example/v1", "test-key", "test-model", 0.2, 512, Duration.ofSeconds(5));
        return new AiModelClient(settings, chatModel);
    }

    @Test
    void returnsStructuredJsonDiagnosisAndFiltersOutHallucinatedTriples() throws Exception {
        PythonKnowledgeClient knowledge = mock(PythonKnowledgeClient.class);
        PythonTriple realOne = new PythonTriple("需求分析", "输出", "软件需求规格说明书", 0.92);
        PythonTriple realTwo = new PythonTriple("需求分析", "使用", "数据流图DFD", 0.85);
        when(knowledge.retrieve(anyString(), anyList(), anyList(), anyList()))
                .thenReturn(new PythonKnowledgeResponse(
                        "需求分析",
                        List.of(new PythonEntity("需求分析", "需求分析", "DICT", 1.0)),
                        List.of(realOne, realTwo),
                        "raw-prompt",
                        true,
                        "template"
                ));

        String fencedJson = """
                ```json
                {
                  "coveredPoints": ["明确了需求分析需要梳理用户业务需求。"],
                  "missingTriples": [
                    {"subject": "需求分析", "predicate": "输出", "object": "软件需求规格说明书"},
                    {"subject": "虚构实体", "predicate": "虚构关系", "object": "虚假对象"}
                  ],
                  "misconceptions": ["注意区分 **需求分析** 与后续系统设计阶段。"],
                  "followUpQuestion": "需求分析完成后通常交付什么规范化文档？"
                }
                ```
                """;
        FeynmanEvaluationService service = new FeynmanEvaluationService(knowledge, modelReturning(fencedJson), json);

        FeynmanEvaluateResponse result = service.evaluate(new FeynmanEvaluateRequest(
                "需求分析",
                "需求分析就是先搞清楚用户想要系统解决什么业务问题。",
                3L
        ));

        assertThat(result.knowledgePoint()).isEqualTo("需求分析");
        assertThat(result.coveredPoints()).containsExactly("明确了需求分析需要梳理用户业务需求。");
        assertThat(result.missingTriples())
                .hasSize(1)
                .first()
                .satisfies(t -> {
                    assertThat(t.subject()).isEqualTo("需求分析");
                    assertThat(t.object()).isEqualTo("软件需求规格说明书");
                    assertThat(t.score()).isEqualTo(0.92);
                });
        assertThat(result.misconceptions()).containsExactly("注意区分 **需求分析** 与后续系统设计阶段。");
        assertThat(result.followUpQuestion()).contains("规范化文档");
        assertThat(result.evidenceTriples()).hasSize(2);

        String serialized = json.writeValueAsString(result);
        assertThat(serialized).doesNotContain("systemPrompt").doesNotContain("只输出一个合法的 JSON 对象");
    }

    @Test
    void degradesGracefullyWhenModelReturnsPlainTextInsteadOfStrictJson() {
        PythonKnowledgeClient knowledge = mock(PythonKnowledgeClient.class);
        PythonTriple realOne = new PythonTriple("数据流图DFD", "组成", "加工", 0.90);
        PythonTriple realTwo = new PythonTriple("数据流图DFD", "组成", "数据存储", 0.88);
        when(knowledge.retrieve(anyString(), anyList(), anyList(), anyList()))
                .thenReturn(new PythonKnowledgeResponse(
                        "数据流图",
                        List.of(),
                        List.of(realOne, realTwo),
                        "raw-prompt",
                        true,
                        "template"
                ));

        FeynmanEvaluationService service = new FeynmanEvaluationService(
                knowledge,
                modelReturning("你已经提到了数据流图DFD里的加工，不过还没有说明数据存储如何表示。"),
                json
        );

        FeynmanEvaluateResponse result = service.evaluate(new FeynmanEvaluateRequest(
                "数据流图",
                "数据流图DFD里面有加工节点负责处理输入数据流。",
                null
        ));

        assertThat(result.coveredPoints()).isNotEmpty();
        assertThat(result.missingTriples()).extracting(FeynmanEvaluateResponse.TripleEvidence::object)
                .containsExactly("数据存储");
        assertThat(result.misconceptions()).singleElement().asString().contains("没有说明数据存储");
        assertThat(result.followUpQuestion()).isNotBlank();
        assertThat(result.evidenceTriples()).hasSize(2);
    }

    @Test
    void throwsUnavailableWhenGraphServiceFailsAndNeverFakesTriples() {
        PythonKnowledgeClient knowledge = mock(PythonKnowledgeClient.class);
        ChatModel chatModel = mock(ChatModel.class);
        ModelSettings settings = new ModelSettings(
                "https://provider.example/v1", "test-key", "test-model", 0.2, 512, Duration.ofSeconds(5));
        when(knowledge.retrieve(anyString(), anyList(), anyList(), anyList()))
                .thenThrow(new KnowledgeServiceUnavailableException("Bridge down", null));

        FeynmanEvaluationService service = new FeynmanEvaluationService(
                knowledge, new AiModelClient(settings, chatModel), json);

        assertThatThrownBy(() -> service.evaluate(new FeynmanEvaluateRequest(
                "需求分析",
                "需求分析旨在明确软件目标、约束条件与验收标准。",
                3L
        ))).isInstanceOf(KnowledgeServiceUnavailableException.class);
        verifyNoInteractions(chatModel);
    }

    @Test
    void handlesEmptyEvidenceHonestlyWithoutCallingLanguageModel() {
        PythonKnowledgeClient knowledge = mock(PythonKnowledgeClient.class);
        ChatModel chatModel = mock(ChatModel.class);
        ModelSettings settings = new ModelSettings(
                "https://provider.example/v1", "test-key", "test-model", 0.2, 512, Duration.ofSeconds(5));
        when(knowledge.retrieve(anyString(), anyList(), anyList(), anyList()))
                .thenReturn(new PythonKnowledgeResponse("未知主题", List.of(), List.of(), "prompt", false, "tpl"));

        FeynmanEvaluationService service = new FeynmanEvaluationService(
                knowledge, new AiModelClient(settings, chatModel), json);

        FeynmanEvaluateResponse result = service.evaluate(new FeynmanEvaluateRequest(
                "未知主题",
                "这是我尝试用自己的话来解释一个未知的软件工程概念。",
                null
        ));

        assertThat(result.evidenceTriples()).isEmpty();
        assertThat(result.missingTriples()).isEmpty();
        assertThat(result.misconceptions()).singleElement().asString().contains("暂未检索到");
        verifyNoInteractions(chatModel);
    }

    @Test
    void passesRaisedMaxTokensBudgetAndSanitizesTruncatedJsonFallback() {
        PythonKnowledgeClient knowledge = mock(PythonKnowledgeClient.class);
        PythonTriple realOne = new PythonTriple("需求分析", "交付文档", "软件需求规格说明书", 0.90);
        PythonTriple realTwo = new PythonTriple("需求分析", "方法", "结构化分析SA", 0.85);
        when(knowledge.retrieve(anyString(), anyList(), anyList(), anyList()))
                .thenReturn(new PythonKnowledgeResponse(
                        "需求分析",
                        List.of(new PythonEntity("需求分析", "需求分析", "DICT", 1.0)),
                        List.of(realOne, realTwo),
                        "raw-prompt",
                        true,
                        "template"
                ));

        ChatModel chatModel = mock(ChatModel.class);
        String truncatedJson = "{\"coveredPoints\":[\"要点1\"],\"missingTriples\":[{\"subject\":\"需求分析\",\"object";
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(truncatedJson)))));

        // 刻意将默认 settings.maxTokens 设为 512，验证费曼调用显式抬升至 4096
        ModelSettings settings = new ModelSettings(
                "https://provider.example/v1", "test-key", "deepseek-flash", 0.2, 512, Duration.ofSeconds(5));
        FeynmanEvaluationService service = new FeynmanEvaluationService(
                knowledge, new AiModelClient(settings, chatModel), json);

        FeynmanEvaluateResponse result = service.evaluate(new FeynmanEvaluateRequest(
                "需求分析",
                "需求分析阶段主要确定软件系统需要具备哪些功能与业务约束条件。",
                1L
        ));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        OpenAiChatOptions capturedOptions = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
        assertThat(capturedOptions).isNotNull();
        assertThat(capturedOptions.getMaxTokens()).isEqualTo(4096);

        assertThat(result.evidenceTriples()).hasSize(2);
        assertThat(result.followUpQuestion()).isNotBlank();
        assertThat(result.misconceptions())
                .singleElement()
                .asString()
                .contains("结构化诊断结果未完整生成")
                .doesNotContain("{")
                .doesNotContain("\"coveredPoints\"")
                .doesNotContain("\"missingTriples\"")
                .doesNotContain("\"subject\"");
    }
}
