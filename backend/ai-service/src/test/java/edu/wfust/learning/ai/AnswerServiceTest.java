package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnswerServiceTest {
    private final PythonKnowledgeClient knowledge = mock(PythonKnowledgeClient.class);
    private final ChatModel model = mock(ChatModel.class);
    private final List<Prompt> submitted = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private final ComparisonExecutor comparisons = new ComparisonExecutor(java.time.Duration.ofSeconds(150));
    @org.junit.jupiter.api.AfterEach void closeComparisons() { comparisons.close(); }
    private AnswerService service;
    private final PythonKnowledgeResponse evidence = new PythonKnowledgeResponse("question", List.of(),
            List.of(new PythonTriple("raw first", "rel", "high", 0.9),
                    new PythonTriple("raw second", "rel", "low", 0.1)),
            "  ORIGINAL {json: [1, 2]}\n问题：question\n  ", true, "make_qa4ft.prompt_template");

    @BeforeEach
    void setUp() {
        when(model.getOptions()).thenReturn(ChatOptions.builder().model("fixed-model")
                .temperature(0.2).maxTokens(128).build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            submitted.add(invocation.getArgument(0));
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().getFirst().getText();
            String answer = system.contains("[EVIDENCE_SELECTION_V1]")
                    ? "{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}"
                    : system.contains("[GROUNDED_ANSWER_V3]") ? "{\"answer\":\"raw firstrel：high。\",\"usedIds\":[\"T1\"]}" : "answer";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))),
                    ChatResponseMetadata.builder().model("fixed-model").usage(new DefaultUsage(12, 4, 16)).build());
        });
        when(knowledge.retrieve(anyString(), anyList(), anyList())).thenReturn(evidence);
        var client = new AiModelClient(new ModelSettings("https://provider.example/v1", "test-key",
                "fixed-model", 0.2, 128, java.time.Duration.ofSeconds(2)), model);
        service = new AnswerService(client, knowledge, new PromptStrategy(json), comparisons, new GroundedRankedAnswer(client, json));
    }

    @Test
    void directBypassesPythonAndSendsTheExactQuestion() {
        AnswerResponse response = service.answer(new AnswerRequest("原始 {question}\n", QaMode.DIRECT));
        assertThat(response.answer()).isEqualTo("answer");
        assertThat(response.entities()).isEmpty();
        assertThat(response.triples()).isEmpty();
        assertThat(response.hasEvidence()).isFalse();
        assertThat(submitted.getFirst().getInstructions().getFirst().getText()).isEqualTo("原始 {question}\n");
        verifyNoInteractions(knowledge);
    }

    @Test
    void interactiveFollowupPassesHistoryToPythonAndLeavesRankedPromptVerbatim() {
        when(knowledge.retrieve(eq("question"), anyList(), anyList(), anyList())).thenReturn(evidence);
        AnswerRequest request = new AnswerRequest("question", QaMode.RANKED,
                List.of("需求分析的文档有哪些"), List.of("需求分析"));
        AnswerResponse result = service.answer(request);
        verify(knowledge).retrieve("question", List.of(), request.historyMessages(), request.historyEntities());
        assertThat(result.prompt()).isEqualTo(evidence.prompt());
        assertThat(submitted.getFirst().getInstructions().getFirst().getText()).isEqualTo(evidence.prompt());
    }

    @Test
    void directWithHistoryStillBypassesPythonAndUsesOnlyTheQuestion() {
        service.answer(new AnswerRequest("question", QaMode.DIRECT, List.of("prior"), List.of("entity")));
        verifyNoInteractions(knowledge);
        assertThat(submitted.getFirst().getInstructions().getFirst().getText()).isEqualTo("question");
    }

    @Test
    void dedicatedRankedModelOnlyHandlesInteractiveRankedAndRecordsItsActualModel() {
        ChatModel rankedChatModel = mock(ChatModel.class);
        when(rankedChatModel.getOptions()).thenReturn(ChatOptions.builder().model("qwen35-ranked-lora")
                .temperature(0.0).maxTokens(512).build());
        when(rankedChatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().getFirst().getText();
            String answer = system.contains("[EVIDENCE_SELECTION_V1]")
                    ? "{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}"
                    : "{\"answer\":\"raw firstrel：high。\",\"usedIds\":[\"T1\"]}";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))),
                    ChatResponseMetadata.builder().model("qwen35-ranked-lora")
                            .usage(new DefaultUsage(10, 6, 16)).build());
        });
        AiModelClient rankedClient = new AiModelClient(new ModelSettings("http://127.0.0.1:8998/v1",
                "qwen-secret", "qwen35-ranked-lora", 0, 512, java.time.Duration.ofSeconds(2)), rankedChatModel);
        AnswerService dedicatedService = new AnswerService(modelClient(), knowledge,
                new PromptStrategy(json), comparisons, new GroundedRankedAnswer(modelClient(), json),
                new GroundedRankedAnswer(rankedClient, json), rankedClient);

        AnswerResponse response = dedicatedService.answer(new AnswerRequest("question", QaMode.RANKED));

        assertThat(response.answer()).isEqualTo("raw firstrel：high。");
        assertThat(response.provenance().model()).isEqualTo("qwen35-ranked-lora");
        assertThat(response.provenance().systemPromptVersion()).isEqualTo(GroundedRankedAnswer.VERSION);
        assertThat(response.grounding()).isNotNull();
        verify(rankedChatModel, times(2)).call(any(Prompt.class));
        verifyNoInteractions(model);
    }

    @Test
    void dedicatedRankedModelDoesNotReceiveDirectTriplesOrComparisonRequests() {
        ChatModel rankedChatModel = mock(ChatModel.class);
        AiModelClient rankedClient = new AiModelClient(new ModelSettings("http://127.0.0.1:8998/v1",
                "qwen-secret", "qwen35-ranked-lora", 0, 512, java.time.Duration.ofSeconds(2)), rankedChatModel);
        AnswerService dedicatedService = new AnswerService(modelClient(), knowledge,
                new PromptStrategy(json), comparisons, new GroundedRankedAnswer(modelClient(), json),
                new GroundedRankedAnswer(rankedClient, json), rankedClient);

        dedicatedService.answer(new AnswerRequest("question", QaMode.DIRECT));
        dedicatedService.answer(new AnswerRequest("question", QaMode.TRIPLES));
        List<AnswerResponse> compared = dedicatedService.compare("question");

        assertThat(compared).extracting(AnswerResponse::mode)
                .containsExactly(QaMode.DIRECT, QaMode.TRIPLES, QaMode.RANKED);
        assertThat(compared).allSatisfy(result ->
                assertThat(result.provenance().model()).isEqualTo("fixed-model"));
        verifyNoInteractions(rankedChatModel);
        assertThat(submitted).hasSize(6);
        assertThat(submitted).allSatisfy(prompt ->
                assertThat(prompt.getOptions().getModel()).isEqualTo("fixed-model"));
    }

    @Test
    void dedicatedRankedModelFailureDoesNotSilentlyFallBackToTheDefaultModel() {
        ChatModel rankedChatModel = mock(ChatModel.class);
        when(rankedChatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("Qwen unavailable"));
        AiModelClient rankedClient = new AiModelClient(new ModelSettings("http://127.0.0.1:8998/v1",
                "qwen-secret", "qwen35-ranked-lora", 0, 512, java.time.Duration.ofSeconds(2)), rankedChatModel);
        AnswerService dedicatedService = new AnswerService(modelClient(), knowledge,
                new PromptStrategy(json), comparisons, new GroundedRankedAnswer(modelClient(), json),
                new GroundedRankedAnswer(rankedClient, json), rankedClient);

        AnswerResponse response = dedicatedService.answer(new AnswerRequest("question", QaMode.RANKED));

        assertThat(response.answer()).isEmpty();
        assertThat(response.errorCode()).isNotBlank();
        assertThat(response.provenance().model()).isEqualTo("qwen35-ranked-lora");
        verify(rankedChatModel).call(any(Prompt.class));
        verifyNoInteractions(model);
    }

    @Test
    void qwenMatrixRunsSixModelMethodPairsWithOneSharedGraphSnapshot() {
        ChatModel baseChat = mock(ChatModel.class);
        ChatModel loraChat = mock(ChatModel.class);
        when(baseChat.getOptions()).thenReturn(ChatOptions.builder().model("qwen35-base").temperature(0.0).maxTokens(512).build());
        when(loraChat.getOptions()).thenReturn(ChatOptions.builder().model("qwen35-ranked-lora").temperature(0.0).maxTokens(512).build());
        configureMatrixChat(baseChat, "qwen35-base", "base answer");
        configureMatrixChat(loraChat, "qwen35-ranked-lora", "lora answer");

        var base = new AiModelClient(new ModelSettings("http://127.0.0.1:8998/v1", "qwen-secret",
                "qwen35-base", 0, 512, java.time.Duration.ofSeconds(2)), baseChat);
        var lora = new AiModelClient(new ModelSettings("http://127.0.0.1:8998/v1", "qwen-secret",
                "qwen35-ranked-lora", 0, 512, java.time.Duration.ofSeconds(2)), loraChat);
        var settings = new RankedModelSettings(true, "http://127.0.0.1:8998/v1", "qwen-secret",
                "qwen35-ranked-lora", "qwen35-base", 0, 512, java.time.Duration.ofSeconds(2), true);
        service.setRankedModelSettings(settings);
        service.setMatrixBaseModelClient(base);
        service.setMatrixAdapterModelClient(lora);
        service.setMatrixBaseGroundedAnswer(new GroundedRankedAnswer(base, json));
        service.setMatrixAdapterGroundedAnswer(new GroundedRankedAnswer(lora, json));

        var results = service.compareQwenMatrix("question");

        assertThat(results).hasSize(6);
        assertThat(results).extracting(AnswerResponse::resultKey).containsExactly(
                "QWEN_BASE_DIRECT", "QWEN_BASE_TRIPLES", "QWEN_BASE_RANKED",
                "QWEN_LORA_DIRECT", "QWEN_LORA_TRIPLES", "QWEN_LORA_RANKED");
        assertThat(results).extracting(AnswerResponse::experimentType).containsOnly("QWEN_MATRIX");
        assertThat(results.subList(0, 3)).allSatisfy(result ->
                assertThat(result.provenance().model()).isEqualTo("qwen35-base"));
        assertThat(results.subList(3, 6)).allSatisfy(result ->
                assertThat(result.provenance().model()).isEqualTo("qwen35-ranked-lora"));
        assertThat(results.stream().map(r -> r.provenance().comparisonId()).distinct()).hasSize(1);
        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        verify(knowledge, times(1)).retrieve(eq("question"), anyList(), anyList());
        verify(baseChat, times(4)).call(any(Prompt.class));
        verify(loraChat, times(4)).call(any(Prompt.class));
        verifyNoInteractions(model);
    }

    private static void configureMatrixChat(ChatModel chat, String modelName, String plainAnswer) {
        when(chat.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().getFirst().getText();
            String answer = system.contains("[EVIDENCE_SELECTION_V1]")
                    ? "{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}"
                    : system.contains("[GROUNDED_ANSWER_V3]")
                    ? "{\"answer\":\"raw firstrel：high。\",\"usedIds\":[\"T1\"]}"
                    : plainAnswer;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))),
                    ChatResponseMetadata.builder().model(modelName).usage(new DefaultUsage(10, 5, 15)).build());
        });
    }

    private AiModelClient modelClient() {
        return new AiModelClient(new ModelSettings("https://provider.example/v1", "test-key",
                "fixed-model", 0.2, 128, java.time.Duration.ofSeconds(2)), model);
    }

    @Test
    void compareRetrievesExactlyOnceAndSharesEvidenceAndGenerationOptions() {
        PythonKnowledgeResponse changed = new PythonKnowledgeResponse("question", List.of(),
                List.of(new PythonTriple("different", "rel", "evidence", 0.5)), "OTHER PROMPT", true, "other");
        when(knowledge.retrieve(anyString(), anyList(), anyList())).thenReturn(evidence, changed);
        List<AnswerResponse> results = service.compare("question");
        assertThat(results).extracting(AnswerResponse::mode).containsExactly(QaMode.DIRECT, QaMode.TRIPLES, QaMode.RANKED);
        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.get(1).triples()).isEqualTo(evidence.triples());
        assertThat(results.get(2).triples()).isEqualTo(evidence.triples());
        assertThat(results.get(2).prompt()).isEqualTo(evidence.prompt());
        assertThat(submitted).hasSize(4).allSatisfy(prompt -> {
            assertThat(prompt.getOptions().getModel()).isEqualTo("fixed-model");
            assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.2);
            assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(128);
        });
        verify(knowledge, times(1)).retrieve(eq("question"), anyList(), anyList());
    }

    @Test
    void rankedComparisonUsesHiddenVersionedSystemMessageAndKeepsOriginalPythonPrompt() {
        List<AnswerResponse> results = service.compare("question");
        AnswerResponse ranked = results.get(2);
        Prompt rankedRequest = submitted.stream()
                .filter(prompt -> prompt.getInstructions().stream().anyMatch(message ->
                        message instanceof UserMessage && message.getText().equals(evidence.prompt())))
                .findFirst().orElseThrow();

        assertThat(ranked.prompt()).isEqualTo(evidence.prompt());
        assertThat(rankedRequest.getInstructions()).hasSize(2);
        assertThat(rankedRequest.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(rankedRequest.getInstructions().get(0).getText())
                .contains("选定的课程证据", "raw first", "high").doesNotContain("动宾短语", "动词+宾语");
        assertThat(rankedRequest.getInstructions().get(1)).isInstanceOf(UserMessage.class);
        assertThat(rankedRequest.getInstructions().get(1).getText()).isEqualTo(evidence.prompt());

        var provenance = json.valueToTree(ranked).path("provenance");
        assertThat(provenance.path("promptSource").asText()).isEqualTo("PYTHON_ORIGINAL_PLUS_EVIDENCE_SELECTION");
        assertThat(provenance.path("systemPromptVersion").asText()).isEqualTo("ranked-grounded-v3");
        assertThat(provenance.path("systemPromptSha256").asText()).hasSize(64);
    }

    @Test
    void pythonFailureStillProducesDirectAndTwoSafeErrorsFromOneAttempt() {
        when(knowledge.retrieve(anyString(), anyList(), anyList()))
                .thenThrow(new IllegalStateException("upstream body sk-test-sensitive"));
        List<AnswerResponse> results = service.compare("question");
        assertThat(results.getFirst().answer()).isEqualTo("answer");
        assertThat(results.getFirst().error()).isNull();
        assertThat(results.subList(1, 3)).allSatisfy(result -> {
            assertThat(result.error()).isNotBlank().doesNotContain("sk-test-sensitive", "upstream body");
            assertThat(result.answer()).isEmpty();
        });
        assertThat(submitted).hasSize(1);
        verify(knowledge, times(1)).retrieve(eq("question"), anyList(), anyList());
    }

    @Test
    void rankedComparisonSelectsDirectEvidenceInsteadOfTrustingTheHighestScoreAndRecordsBothCalls() {
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            submitted.add(prompt);
            String system = prompt.getInstructions().getFirst().getText();
            String text = system.contains("[EVIDENCE_SELECTION_V1]")
                    ? "{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T2\"]}"
                    : prompt.getInstructions().getLast().getText().equals(evidence.prompt())
                    ? "{\"answer\":\"raw secondrel：low。\",\"usedIds\":[\"T2\"]}" : "answer";
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(12, 4, 16)).build());
        });

        var result = service.compare("question").get(2);
        assertThat(result.error()).isNull();
        assertThat(result.answer()).isEqualTo("raw secondrel：low。");
        var tree = json.valueToTree(result);
        assertThat(tree.path("grounding").path("selectedIds").get(0).asText()).isEqualTo("T2");
        assertThat(tree.path("grounding").path("candidates")).hasSize(2);
        assertThat(tree.path("grounding").path("modelCallCount").asInt()).isEqualTo(2);
        assertThat(tree.path("grounding").path("selectionRawResponse").asText()).contains("T2");
        assertThat(tree.path("grounding").path("generationRawResponse").asText()).contains("raw second");
        assertThat(result.usage().totalTokens()).isEqualTo(32);
    }

    @Test
    void rankedPromptIncludingWhitespaceAndBracesIsVerbatimAtModelBoundary() {
        AnswerResponse result = service.answer(new AnswerRequest("question", QaMode.RANKED));
        assertThat(result.prompt()).isEqualTo(evidence.prompt());
        assertThat(submitted.getFirst().getInstructions()).hasSize(1);
        assertThat(submitted.getFirst().getInstructions().getFirst().getText()).isEqualTo(evidence.prompt());
    }

    @Test
    void resultIncludesServerProvenanceTimingsAndAvailableUsage() {
        List<AnswerResponse> results = service.compare("question");
        var direct = json.valueToTree(results.getFirst());
        var triples = json.valueToTree(results.get(1));
        var ranked = json.valueToTree(results.get(2));
        assertThat(direct.path("provenance").path("model").asText()).isEqualTo("fixed-model");
        assertThat(direct.path("provenance").path("evidenceId").isNull()).isTrue();
        assertThat(triples.path("provenance").path("evidenceId").asText()).isNotBlank()
                .isEqualTo(ranked.path("provenance").path("evidenceId").asText());
        assertThat(direct.path("provenance").path("comparisonId").asText()).isNotBlank()
                .isEqualTo(triples.path("provenance").path("comparisonId").asText());
        assertThat(ranked.path("usage").path("promptTokens").asInt()).isEqualTo(24);
        assertThat(ranked.path("usage").path("completionTokens").asInt()).isEqualTo(8);
        assertThat(ranked.path("usage").path("totalTokens").asInt()).isEqualTo(32);
        assertThat(ranked.path("timings").has("retrievalMs")).isTrue();
        assertThat(ranked.path("timings").has("generationMs")).isTrue();
    }

    @Test
    void optionalAblationsUseOneRawSnapshotAndVaryOnlyOrderAndScore() {
        when(knowledge.retrieveUntruncated(eq("question"))).thenReturn(evidence);
        List<AnswerResponse> results = service.compare("question", true);
        assertThat(results).extracting(AnswerResponse::mode).containsExactly(QaMode.DIRECT, QaMode.TRIPLES,
                QaMode.RANKED, QaMode.ABLATION_SORTED_SCORED, QaMode.ABLATION_WITHOUT_SCORE,
                QaMode.ABLATION_WITHOUT_SORT, QaMode.ABLATION_WITHOUT_SORT_AND_SCORE);
        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results.get(2).prompt()).isEqualTo(evidence.prompt());
        assertThat(results.subList(1, 7)).allSatisfy(result -> {
            assertThat(result.triples()).containsExactlyInAnyOrderElementsOf(evidence.triples());
            assertThat(json.valueToTree(result).path("provenance").path("topK").asInt()).isZero();
        });
        String sortedScored = results.get(3).prompt();
        String sortedUnscored = results.get(4).prompt();
        String rawScored = results.get(5).prompt();
        String rawUnscored = results.get(6).prompt();
        assertThat(sortedScored.indexOf("raw second")).isLessThan(sortedScored.indexOf("raw first"));
        assertThat(sortedUnscored.indexOf("raw second")).isLessThan(sortedUnscored.indexOf("raw first"));
        assertThat(rawScored.indexOf("raw first")).isLessThan(rawScored.indexOf("raw second"));
        assertThat(rawUnscored.indexOf("raw first")).isLessThan(rawUnscored.indexOf("raw second"));
        assertThat(sortedScored).contains("0.9", "0.1");
        assertThat(rawScored).contains("0.9", "0.1");
        assertThat(sortedUnscored).doesNotContain("0.9", "0.1");
        assertThat(rawUnscored).doesNotContain("0.9", "0.1");
        verify(knowledge, times(1)).retrieveUntruncated("question");
        verify(knowledge, never()).retrieve(anyString(), anyList(), anyList());
    }

    @Test
    void interactiveEndpointsRejectExperimentModesBeforeAnyUpstreamWork() {
        assertThatThrownBy(() -> service.answer(new AnswerRequest("question", QaMode.ABLATION_WITHOUT_SORT)))
                .isInstanceOf(AiFailure.class).hasMessageContaining("includeAblations");
        verifyNoInteractions(knowledge);
        assertThat(submitted).isEmpty();
    }

    @Test
    void modelFailureRetainsRetrievedEvidenceAndPromptWithoutExposingProviderBody() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("Bearer sk-test-sensitive"));
        List<AnswerResponse> results = service.compare("question");
        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNotBlank().doesNotContain("sk-test-sensitive"));
        assertThat(results.get(2).triples()).isEqualTo(evidence.triples());
        assertThat(results.get(2).prompt()).isEqualTo(evidence.prompt());
    }
}
