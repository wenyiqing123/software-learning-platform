package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Compatibility facade for interactive answers, comparisons and SSE callers. */
@Service
public class AnswerService {
    private final AiModelClient modelClient;
    private final PythonKnowledgeClient knowledgeClient;
    private final PromptStrategy promptStrategy;
    private final ComparisonExecutor comparisons;
    private final GroundedRankedAnswer groundedRanked;
    private QaStrategySelector strategySelector = QaStrategySelector.defaultSelector();
    private GroundedRankedAnswer dedicatedGroundedRanked;
    private AiModelClient dedicatedRankedModel;
    private RankedModelSettings rankedModelSettings;
    private AiModelClient matrixBaseModelClient;
    private AiModelClient matrixAdapterModelClient;
    private GroundedRankedAnswer matrixBaseGroundedAnswer;
    private GroundedRankedAnswer matrixAdapterGroundedAnswer;

    @org.springframework.beans.factory.annotation.Autowired
    public AnswerService(@org.springframework.beans.factory.annotation.Qualifier("aiModelClient") AiModelClient modelClient,
                         PythonKnowledgeClient knowledgeClient, PromptStrategy promptStrategy,
                         ComparisonExecutor comparisons,
                         @org.springframework.beans.factory.annotation.Qualifier("defaultGroundedRankedAnswer") GroundedRankedAnswer groundedRanked) {
        this.modelClient = modelClient;
        this.knowledgeClient = knowledgeClient;
        this.promptStrategy = promptStrategy;
        this.comparisons = comparisons;
        this.groundedRanked = groundedRanked;
    }

    AnswerService(AiModelClient modelClient, PythonKnowledgeClient knowledgeClient, PromptStrategy promptStrategy,
                  ComparisonExecutor comparisons, GroundedRankedAnswer groundedRanked,
                  GroundedRankedAnswer dedicatedGroundedRanked, AiModelClient dedicatedRankedModel) {
        this(modelClient, knowledgeClient, promptStrategy, comparisons, groundedRanked);
        this.dedicatedGroundedRanked = dedicatedGroundedRanked;
        this.dedicatedRankedModel = dedicatedRankedModel;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("rankedGroundedRankedAnswer")
    void setDedicatedGroundedRanked(GroundedRankedAnswer value) { this.dedicatedGroundedRanked = value; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("rankedModelClient")
    void setDedicatedRankedModel(AiModelClient value) { this.dedicatedRankedModel = value; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRankedModelSettings(RankedModelSettings value) { this.rankedModelSettings = value; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("matrixBaseModelClient")
    void setMatrixBaseModelClient(AiModelClient value) { this.matrixBaseModelClient = value; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("matrixAdapterModelClient")
    void setMatrixAdapterModelClient(AiModelClient value) { this.matrixAdapterModelClient = value; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("matrixBaseGroundedRankedAnswer")
    void setMatrixBaseGroundedAnswer(GroundedRankedAnswer value) { this.matrixBaseGroundedAnswer = value; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("matrixAdapterGroundedRankedAnswer")
    void setMatrixAdapterGroundedAnswer(GroundedRankedAnswer value) { this.matrixAdapterGroundedAnswer = value; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setStrategySelector(QaStrategySelector value) { if (value != null) this.strategySelector = value; }

    public ModelSettings settings() { return modelClient.settings(); }
    public void requireConfigured() { modelClient.requireConfigured(); }
    public void requireInteractive(AnswerRequest request) {
        if (request.mode().experimentOnly()) throw AiFailure.interactiveMode();
        requireConfigured();
    }

    public Map<String, Object> rankedModelInfo() {
        RankedModelSettings settings = rankedModelSettings;
        boolean enabled = settings != null && settings.enabled();
        boolean configured = settings != null && settings.configured();
        String model = settings == null ? "qwen35-ranked-lora" : settings.modelSettings().model();
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("rankedModelEnabled", enabled);
        info.put("rankedModelConfigured", configured);
        info.put("rankedModel", model);
        info.put("rankedModelStatus", !enabled ? "DISABLED" : configured ? "CONFIGURED" : "NOT_CONFIGURED");
        boolean matrixEnabled = settings != null && settings.matrixEnabled();
        boolean matrixConfigured = settings != null && settings.matrixConfigured()
                && matrixBaseModelClient != null && matrixAdapterModelClient != null
                && matrixBaseGroundedAnswer != null && matrixAdapterGroundedAnswer != null;
        info.put("qwenMatrixEnabled", matrixEnabled);
        info.put("qwenMatrixConfigured", matrixConfigured);
        info.put("qwenMatrixStatus", !matrixEnabled ? "DISABLED" : matrixConfigured ? "CONFIGURED" : "NOT_CONFIGURED");
        if (matrixEnabled) {
            info.put("qwenMatrixBaseModel", settings.baseModel());
            info.put("qwenMatrixAdapterModel", model);
        }
        return Map.copyOf(info);
    }

    public PreparedAnswer prepare(AnswerRequest request) {
        requireInteractive(request);
        AnswerSnapshot snapshot = request.mode() == QaMode.DIRECT ? AnswerSnapshot.direct()
                : preparation().retrieveSnapshot(request, false);
        if (snapshot.failure() != null) throw snapshot.failure();
        return preparation().prepare(request, snapshot, null);
    }

    public AnswerResponse answer(AnswerRequest request) { return execution().execute(prepare(request), false); }
    boolean usesDedicatedRankedModel(PreparedAnswer prepared) { return execution().usesDedicatedRankedModel(prepared); }
    AnswerResponse executePrepared(PreparedAnswer prepared) { return execution().execute(prepared, false); }
    public Flux<ChatResponse> stream(PreparedAnswer prepared) { return execution().stream(prepared); }
    public AnswerResponse completed(PreparedAnswer prepared, String answer, long totalMs, long generationMs,
                                    AnswerResponse.TokenUsage usage) { return execution().completed(prepared, answer, totalMs, generationMs, usage); }
    public List<AnswerResponse> compare(String question) { return compare(question, false); }
    public List<AnswerResponse> compare(String question, boolean includeAblations) {
        return comparison().compare(question, includeAblations, this::requireConfigured);
    }
    public List<AnswerResponse> compareQwenMatrix(String question) { return comparison().compareQwenMatrix(question); }

    private AnswerPreparationCoordinator preparation() {
        return new AnswerPreparationCoordinator(knowledgeClient, promptStrategy, modelClient,
                dedicatedRankedModel, dedicatedGroundedRanked);
    }
    private AnswerExecutionCoordinator execution() {
        return new AnswerExecutionCoordinator(modelClient, promptStrategy, groundedRanked, strategySelector,
                dedicatedRankedModel, dedicatedGroundedRanked, matrixBaseModelClient, matrixAdapterModelClient,
                matrixBaseGroundedAnswer, matrixAdapterGroundedAnswer);
    }
    private AnswerComparisonCoordinator comparison() {
        return new AnswerComparisonCoordinator(comparisons, preparation(), execution(), rankedModelSettings,
                matrixBaseModelClient, matrixAdapterModelClient, matrixBaseGroundedAnswer, matrixAdapterGroundedAnswer);
    }

    static long elapsedMs(long startedAt) { return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000); }
    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record PreparedAnswer(AnswerRequest request, String prompt, edu.wfust.learning.ai.client.PythonKnowledgeResponse knowledge,
                                 AnswerResponse.Provenance provenance, long retrievalMs) { }
}
