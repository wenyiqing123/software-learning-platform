package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiControllerTest {
    private final ExecutorService workers = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
    private final PythonKnowledgeClient python = mock(PythonKnowledgeClient.class);
    private final ChatModel model = mock(ChatModel.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final ComparisonExecutor comparisons = new ComparisonExecutor(Duration.ofSeconds(150));
    private MockMvc mvc;
    private AnswerService answerService;
    private AiModelClient modelClient;

    @AfterEach void close() { comparisons.close(); workers.shutdownNow(); deadlines.shutdownNow(); }
    private void setup(String key) {
        modelClient = new AiModelClient(new ModelSettings("https://provider.example/v1", key,
                "fixed-model", 0.2, 128, Duration.ofSeconds(1)), model);
        answerService = new AnswerService(modelClient, python, new PromptStrategy(json), comparisons, new GroundedRankedAnswer(modelClient, json));
        mvc = MockMvcBuilders.standaloneSetup(new AiController(answerService, python, workers, deadlines, Duration.ofSeconds(2)),
                        new AiMicroLessonController(modelClient))
                .setControllerAdvice(new AiExceptionHandler()).build();
    }

    @Test void microLessonUsesDirectGenerationProfileAndLargerBudget() throws Exception {
        setup("test-key");
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("微课正文")))));

        var result = mvc.perform(post("/api/ai/micro-lesson").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"加工—命名—动宾短语\"}"))
                .andExpect(status().isOk()).andReturn();

        assertThat(json.readTree(result.getResponse().getContentAsString()).path("answer").asText()).isEqualTo("微课正文");
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions().getFirst().getText()).contains("直接输出", "不得返回空内容");
        assertThat(prompt.getValue().getOptions().getMaxTokens()).isEqualTo(2048);
    }

    @Test void dependenciesReportRankedModelStateWithoutExposingAddressOrCredentials() throws Exception {
        setup("test-key");
        answerService.setRankedModelSettings(RankedModelSettings.forRanked(true, "http://127.0.0.1:8998/v1",
                "ranked-test-secret", "qwen35-ranked-lora", 0, 512, Duration.ofSeconds(90)));

        var result = mvc.perform(get("/api/ai/dependencies")).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("rankedModelEnabled").asBoolean()).isTrue();
        assertThat(body.path("rankedModelConfigured").asBoolean()).isTrue();
        assertThat(body.path("rankedModel").asText()).isEqualTo("qwen35-ranked-lora");
        assertThat(body.path("rankedModelStatus").asText()).isEqualTo("CONFIGURED");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("ranked-test-secret", "8998", "127.0.0.1");
    }

    @Test void matrixHealthIsPublicAndReportsOnlySafeQwenState() throws Exception {
        setup("test-key");
        answerService.setRankedModelSettings(RankedModelSettings.forMatrix(true,
                "http://127.0.0.1:8998/v1", "qwen-test-secret", "qwen35-ranked-lora", "qwen35-base",
                0, 512, Duration.ofSeconds(90), true));
        var baseClient = new AiModelClient(new ModelSettings("https://provider.example/v1", "test-key",
                "qwen35-base", 0, 512, Duration.ofSeconds(2)), model);
        var adapterClient = new AiModelClient(new ModelSettings("https://provider.example/v1", "test-key",
                "qwen35-ranked-lora", 0, 512, Duration.ofSeconds(2)), model);
        answerService.setMatrixBaseModelClient(baseClient);
        answerService.setMatrixAdapterModelClient(adapterClient);
        answerService.setMatrixBaseGroundedAnswer(new GroundedRankedAnswer(baseClient, json));
        answerService.setMatrixAdapterGroundedAnswer(new GroundedRankedAnswer(adapterClient, json));

        var result = mvc.perform(get("/api/ai/matrix-health")).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.path("qwenMatrixEnabled").asBoolean()).isTrue();
        assertThat(body.path("qwenMatrixConfigured").asBoolean()).isTrue();
        assertThat(body.path("qwenMatrixBaseModel").asText()).isEqualTo("qwen35-base");
        assertThat(body.path("qwenMatrixAdapterModel").asText()).isEqualTo("qwen35-ranked-lora");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("qwen-test-secret", "8998", "127.0.0.1");
    }

    @Test void missingKeyReturns503AndConfiguredFalseWithoutExternalCalls() throws Exception {
        setup("");
        for (String endpoint : List.of("/api/ai/ask", "/api/ai/compare", "/api/ai/ask/stream")) {
            var result = mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .accept(endpoint.endsWith("stream") ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"question\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
            var body = json.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("configured").asBoolean()).isFalse();
            assertThat(body.path("code").asText()).isEqualTo("MODEL_NOT_CONFIGURED");
            assertThat(body.path("message").asText()).contains("OPENAI_API_KEY");
            assertThat(result.getRequest().isAsyncStarted()).isFalse();
        }
        var dependencies = mvc.perform(get("/api/ai/dependencies")).andExpect(status().isOk()).andReturn();
        var dependencyBody = json.readTree(dependencies.getResponse().getContentAsString());
        assertThat(dependencyBody.path("pythonKnowledgeService").isNull()).isTrue();
        assertThat(dependencyBody.path("pythonKnowledgeStatus").asText()).isEqualTo("NOT_CHECKED");
        assertThat(dependencyBody.path("rankedModelEnabled").asBoolean()).isFalse();
        assertThat(dependencyBody.path("rankedModelConfigured").asBoolean()).isFalse();
        assertThat(dependencyBody.path("rankedModelStatus").asText()).isEqualTo("DISABLED");
        verifyNoInteractions(model, python);
    }

    @Test void compareRemainsAListWithThreeDefaultResultsAndSevenOptInResults() throws Exception {
        setup("test-key");
        var evidence = new PythonKnowledgeResponse("question", List.of(), List.of(), "ORIGINAL {}\n", false, "template");
        when(python.retrieve(anyString(), anyList(), anyList())).thenReturn(evidence);
        when(python.retrieveUntruncated(anyString())).thenReturn(evidence);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("OK")))));
        for (String body : List.of("{\"question\":\"question\"}",
                "{\"question\":\"question\",\"includeAblations\":true}")) {
            var result = mvc.perform(post("/api/ai/compare").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andReturn();
            var list = json.readTree(result.getResponse().getContentAsString());
            assertThat(list.isArray()).isTrue();
            assertThat(list.size()).isEqualTo(body.contains("true") ? 7 : 3);
            assertThat(list.get(0).path("mode").asText()).isEqualTo("DIRECT");
            assertThat(list.get(2).path("prompt").asText()).isEqualTo(evidence.prompt());
            assertThat(list.get(2).path("usage").isNull()).isTrue();
            assertThat(list.get(2).path("provenance").path("maxTokens").asInt()).isEqualTo(128);
        }
    }

    @Test void invalidHistoryAndExperimentModesProduceSafe400IncludingStreamAccept() throws Exception {
        setup("test-key");
        for (String body : List.of("{\"question\":\"question\",\"historyMessages\":[\" \"]}",
                "{\"question\":\"question\",\"historyEntities\":[\"" + "x".repeat(201) + "\"]}",
                "{\"question\":\"question\",\"mode\":\"ABLATION_WITHOUT_SORT\"}")) {
            mvc.perform(post("/api/ai/ask/stream").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM).content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
        verifyNoInteractions(model, python);
    }
}
