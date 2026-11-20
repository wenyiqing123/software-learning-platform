package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class AnswerService {
    private static final List<QaMode> PRIMARY = List.of(QaMode.DIRECT, QaMode.TRIPLES, QaMode.RANKED);
    private static final List<QaMode> ABLATIONS = List.of(QaMode.ABLATION_SORTED_SCORED, QaMode.ABLATION_WITHOUT_SCORE,
            QaMode.ABLATION_WITHOUT_SORT, QaMode.ABLATION_WITHOUT_SORT_AND_SCORE);
    private final AiModelClient modelClient;
    private final PythonKnowledgeClient knowledgeClient;
    private final PromptStrategy promptStrategy;
    private final ComparisonExecutor comparisons;
    private final GroundedRankedAnswer groundedRanked;
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
                         @org.springframework.beans.factory.annotation.Qualifier("defaultGroundedRankedAnswer")
                         GroundedRankedAnswer groundedRanked) {
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
    public ModelSettings settings() { return modelClient.settings(); }
    public Map<String, Object> rankedModelInfo() {
        RankedModelSettings settings = rankedModelSettings;
        boolean enabled = settings != null && settings.enabled();
        boolean configured = settings != null && settings.configured();
        String model = settings == null ? "qwen35-ranked-lora" : settings.modelSettings().model();
        String status = !enabled ? "DISABLED" : configured ? "CONFIGURED" : "NOT_CONFIGURED";
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("rankedModelEnabled", enabled);
        info.put("rankedModelConfigured", configured);
        info.put("rankedModel", model);
        info.put("rankedModelStatus", status);
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
    public void requireConfigured() { modelClient.requireConfigured(); }
    public void requireInteractive(AnswerRequest request) {
        if (request.mode().experimentOnly()) throw AiFailure.interactiveMode();
        requireConfigured();
    }

    public PreparedAnswer prepare(AnswerRequest request) {
        requireInteractive(request);
        Snapshot snapshot = request.mode() == QaMode.DIRECT ? Snapshot.direct()
                : retrieveSnapshot(request, false);
        if (snapshot.failure() != null) throw snapshot.failure();
        return prepare(request, snapshot, null);
    }

    public AnswerResponse answer(AnswerRequest request) {
        return execute(prepare(request), false);
    }

    boolean usesDedicatedRankedModel(PreparedAnswer prepared) {
        return dedicatedGroundedRanked != null && prepared.request().mode() == QaMode.RANKED
                && prepared.provenance().comparisonId() == null;
    }

    AnswerResponse executePrepared(PreparedAnswer prepared) { return execute(prepared, false); }

    public Flux<ChatResponse> stream(PreparedAnswer prepared) {
        return modelClient.stream(prepared.prompt());
    }

    public AnswerResponse completed(PreparedAnswer prepared, String answer, long totalMs, long generationMs,
                                    AnswerResponse.TokenUsage usage) {
        return response(prepared, answer, totalMs, generationMs, usage, null);
    }

    public List<AnswerResponse> compare(String question) { return compare(question, false); }

    public List<AnswerResponse> compareQwenMatrix(String question) {
        if (rankedModelSettings == null) throw matrixUnavailable();
        rankedModelSettings.requireMatrixConfigured();
        if (matrixBaseModelClient == null || matrixAdapterModelClient == null
                || matrixBaseGroundedAnswer == null || matrixAdapterGroundedAnswer == null) throw matrixUnavailable();

        requireMatrixModel(matrixBaseModelClient, rankedModelSettings.baseModel());
        requireMatrixModel(matrixAdapterModelClient, rankedModelSettings.modelSettings().model());

        long deadline = comparisons.deadline();
        String comparisonId = UUID.randomUUID().toString();
        Snapshot snapshot = retrieveSnapshot(question, false);
        List<AnswerResponse> results = new ArrayList<>(6);
        for (String variant : List.of("BASE", "LORA")) {
            for (QaMode mode : PRIMARY) {
                Snapshot methodSnapshot = mode == QaMode.DIRECT ? Snapshot.direct() : snapshot;
                PreparedAnswer prepared = matrixPrepared(question, methodSnapshot, comparisonId, mode, variant);
                if (mode != QaMode.DIRECT && snapshot.failure() != null) {
                    results.add(matrixError(prepared, snapshot.failure(), 0));
                    continue;
                }
                if (comparisons.expired(deadline)) {
                    results.add(matrixError(prepared, AiFailure.comparisonTimeout(), 0));
                    continue;
                }
                long started = System.nanoTime();
                try {
                    AnswerResponse result = comparisons.submit(() -> executeMatrix(prepared, variant, true))
                            .get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    results.add(result);
                } catch (TimeoutException exception) {
                    results.add(matrixError(prepared, AiFailure.comparisonTimeout(), elapsedMs(started)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    results.add(matrixError(prepared, AiFailure.comparisonTimeout(), elapsedMs(started)));
                } catch (ExecutionException exception) {
                    AiFailure failure = exception.getCause() instanceof AiFailure known ? known : AiFailure.model(exception.getCause());
                    results.add(matrixError(prepared, failure, elapsedMs(started)));
                }
            }
        }
        return List.copyOf(results);
    }

    public List<AnswerResponse> compare(String question, boolean includeAblations) {
        // Validate before scheduling any upstream work. All arms use the same immutable generation settings.
        requireConfigured();
        long deadline = comparisons.deadline();
        String comparisonId = UUID.randomUUID().toString();
        List<PendingAnswer> pending = new ArrayList<>();
        Future<Snapshot> retrieval = null;
        try {
            pending.add(submit(prepare(new AnswerRequest(question, QaMode.DIRECT), Snapshot.direct(), comparisonId), deadline));
            Snapshot snapshot;
            long retrievalStarted = System.nanoTime();
            try {
                retrieval = comparisons.submit(() -> retrieveSnapshot(question, includeAblations));
                snapshot = comparisons.await(retrieval, deadline);
            } catch (TimeoutException exception) {
                snapshot = failedSnapshot(includeAblations, retrievalStarted, AiFailure.comparisonTimeout());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                snapshot = failedSnapshot(includeAblations, retrievalStarted, AiFailure.comparisonTimeout());
            } catch (ExecutionException | RuntimeException exception) {
                AiFailure failure = exception instanceof AiFailure known ? known : AiFailure.knowledge(exception);
                snapshot = failedSnapshot(includeAblations, retrievalStarted, failure);
            }
            List<QaMode> evidenceModes = new ArrayList<>(PRIMARY.subList(1, PRIMARY.size()));
            if (includeAblations) evidenceModes.addAll(ABLATIONS);
            for (QaMode mode : evidenceModes) {
                AnswerRequest request = new AnswerRequest(question, mode);
                if (snapshot.failure() != null) {
                    pending.add(new PendingAnswer(prepared(request, "", snapshot, comparisonId), null, snapshot.failure(), System.nanoTime()));
                    continue;
                }
                try {
                    pending.add(submit(prepare(request, snapshot, comparisonId), deadline));
                } catch (AiFailure failure) {
                    pending.add(new PendingAnswer(prepared(request, "", snapshot, comparisonId), null, failure, System.nanoTime()));
                }
            }
            return pending.stream().map(item -> await(item, deadline)).toList();
        } finally {
            comparisons.cancel(retrieval);
            pending.forEach(item -> comparisons.cancel(item.future()));
        }
    }

    private PendingAnswer submit(PreparedAnswer prepared, long deadline) {
        long startedAt = System.nanoTime();
        if (comparisons.expired(deadline)) return new PendingAnswer(prepared, null, AiFailure.comparisonTimeout(), startedAt);
        try { return new PendingAnswer(prepared, comparisons.submit(() -> execute(prepared, true)), null, startedAt); }
        catch (AiFailure failure) { return new PendingAnswer(prepared, null, failure, startedAt); }
    }

    private AnswerResponse await(PendingAnswer pending, long deadline) {
        AiFailure failure = pending.failure();
        if (failure == null) {
            try { return comparisons.await(pending.future(), deadline); }
            catch (TimeoutException exception) { failure = AiFailure.comparisonTimeout(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); failure = AiFailure.comparisonTimeout(); }
            catch (ExecutionException exception) { failure = AiFailure.model(exception); }
        }
        long generationMs = pending.future() == null ? 0 : elapsedMs(pending.startedAt());
        return response(pending.prepared(), "", pending.prepared().retrievalMs() + generationMs, generationMs, null, failure);
    }

    private Snapshot failedSnapshot(boolean rawOrder, long startedAt, AiFailure failure) {
        return new Snapshot(null, null, UUID.randomUUID().toString(), elapsedMs(startedAt), failure, rawOrder ? 0 : 20, rawOrder);
    }

    private record PendingAnswer(PreparedAnswer prepared, Future<AnswerResponse> future, AiFailure failure, long startedAt) { }

    private Snapshot retrieveSnapshot(String question, boolean rawOrder) {
        return retrieveSnapshot(new AnswerRequest(question, QaMode.RANKED), rawOrder);
    }

    private Snapshot retrieveSnapshot(AnswerRequest request, boolean rawOrder) {
        String question = request.question();
        long startedAt = System.nanoTime();
        String retrievalId = UUID.randomUUID().toString();
        try {
            PythonKnowledgeResponse knowledge = rawOrder ? knowledgeClient.retrieveUntruncated(question)
                    : request.historyMessages().isEmpty() && request.historyEntities().isEmpty()
                    ? knowledgeClient.retrieve(question, List.of(), List.of())
                    : knowledgeClient.retrieve(question, List.of(), request.historyMessages(), request.historyEntities());
            if (knowledge == null) throw AiFailure.knowledge(null);
            return new Snapshot(knowledge, UUID.randomUUID().toString(), retrievalId, elapsedMs(startedAt),
                    null, rawOrder ? 0 : 20, rawOrder);
        } catch (RuntimeException exception) {
            AiFailure failure = exception instanceof AiFailure known ? known : AiFailure.knowledge(exception);
            return new Snapshot(null, null, retrievalId, elapsedMs(startedAt), failure, rawOrder ? 0 : 20, rawOrder);
        }
    }

    private PreparedAnswer prepare(AnswerRequest request, Snapshot snapshot, String comparisonId) {
        if (request.mode().requiresRawOrder() && !snapshot.rawOrder()) throw AiFailure.ablationLimit();
        return prepared(request, promptStrategy.build(request.mode(), request.question(), snapshot.knowledge()), snapshot, comparisonId);
    }

    private PreparedAnswer prepared(AnswerRequest request, String prompt, Snapshot snapshot, String comparisonId) {
        QaMode mode = request.mode();
        ModelSettings settings = settings();
        boolean direct = mode == QaMode.DIRECT;
        boolean dedicatedRanked = mode == QaMode.RANKED && comparisonId == null && dedicatedGroundedRanked != null;
        String systemPromptVersion = (comparisonId != null && mode == QaMode.RANKED) || dedicatedRanked
                ? GroundedRankedAnswer.VERSION : null;
        String source = direct ? "QUESTION" : dedicatedRanked ? "PYTHON_ORIGINAL_PLUS_QWEN_EVIDENCE_SELECTION"
                : systemPromptVersion != null ? "PYTHON_ORIGINAL_PLUS_EVIDENCE_SELECTION"
                : mode == QaMode.RANKED ? "PYTHON_ORIGINAL"
                : mode.experimentOnly() ? "JAVA_FACTORIAL_V1" : "JAVA_TRIPLES_V1";
        String order = direct ? "NONE" : mode == QaMode.RANKED ? "PYTHON_ORIGINAL"
                : mode == QaMode.ABLATION_SORTED_SCORED || mode == QaMode.ABLATION_WITHOUT_SCORE ? "SCORE_ASCENDING"
                : snapshot.rawOrder() ? "PYTHON_RAW" : "PYTHON_RETURNED";
        String evidenceStatus = direct ? "NOT_REQUESTED" : snapshot.failure() != null ? "FAILED"
                : snapshot.knowledge().hasEvidence() ? "AVAILABLE" : "EMPTY";
        var provenance = new AnswerResponse.Provenance(UUID.randomUUID().toString(), comparisonId,
                snapshot.evidenceId(), snapshot.retrievalId(), evidenceStatus, source,
                snapshot.knowledge() == null ? null : snapshot.knowledge().promptTemplate(), sha256(prompt),
                systemPromptVersion, null,
                (dedicatedRankedModel == null || !dedicatedRanked ? settings : dedicatedRankedModel.settings()).model(),
                (dedicatedRankedModel == null || !dedicatedRanked ? settings : dedicatedRankedModel.settings()).temperature(),
                (dedicatedRankedModel == null || !dedicatedRanked ? settings : dedicatedRankedModel.settings()).maxTokens(), direct ? null : 1,
                direct ? null : snapshot.topK(), direct ? "NONE" : snapshot.rawOrder() ? "PYTHON_RAW" : "PYTHON_RETURNED",
                order, mode.injectsScores(), snapshot.rawOrder() && snapshot.knowledge() != null);
        return new PreparedAnswer(request, prompt, snapshot.knowledge(), provenance,
                mode == QaMode.DIRECT ? 0 : snapshot.retrievalMs());
    }

    private void requireMatrixModel(AiModelClient client, String expectedModel) {
        if (!client.settings().model().equals(expectedModel)) throw matrixUnavailable();
        client.requireConfigured();
    }

    private AiFailure matrixUnavailable() {
        return new AiFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "QWEN_MATRIX_UNAVAILABLE", "本地 Qwen 基座/LoRA 对照配置暂不可用。", false);
    }

    private PreparedAnswer matrixPrepared(String question, Snapshot snapshot, String comparisonId,
                                          QaMode mode, String variant) {
        AnswerRequest request = new AnswerRequest(question, mode);
        String prompt = promptStrategy.build(mode, question, snapshot.knowledge());
        ModelSettings modelSettings = matrixClient(variant).settings();
        String systemVersion = mode == QaMode.RANKED ? GroundedRankedAnswer.VERSION : null;
        String source = mode == QaMode.DIRECT ? "QUESTION"
                : mode == QaMode.RANKED ? "PYTHON_ORIGINAL_PLUS_MODEL_EVIDENCE_SELECTION" : "JAVA_TRIPLES_V1";
        String order = mode == QaMode.DIRECT ? "NONE"
                : mode == QaMode.RANKED ? "PYTHON_ORIGINAL" : "PYTHON_RETURNED";
        var provenance = new AnswerResponse.Provenance(UUID.randomUUID().toString(), comparisonId,
                snapshot.evidenceId(), snapshot.retrievalId(), mode == QaMode.DIRECT ? "NOT_REQUESTED"
                : snapshot.knowledge().hasEvidence() ? "AVAILABLE" : "EMPTY",
                source, snapshot.knowledge() == null ? null : snapshot.knowledge().promptTemplate(),
                sha256(prompt), systemVersion, null, modelSettings.model(), modelSettings.temperature(),
                modelSettings.maxTokens(), mode == QaMode.DIRECT ? null : 1, mode == QaMode.DIRECT ? null : snapshot.topK(),
                mode == QaMode.DIRECT ? "NONE" : snapshot.rawOrder() ? "PYTHON_RAW" : "PYTHON_RETURNED",
                order, mode == QaMode.RANKED, snapshot.rawOrder() && snapshot.knowledge() != null);
        return new PreparedAnswer(request, prompt, snapshot.knowledge(), provenance, snapshot.retrievalMs());
    }

    private AiModelClient matrixClient(String variant) {
        return variant.equals("BASE") ? matrixBaseModelClient : matrixAdapterModelClient;
    }

    private GroundedRankedAnswer matrixGrounded(String variant) {
        return variant.equals("BASE") ? matrixBaseGroundedAnswer : matrixAdapterGroundedAnswer;
    }

    private AnswerResponse executeMatrix(PreparedAnswer prepared, String variant, boolean tolerateFailure) {
        long startedAt = System.nanoTime();
        try {
            if (prepared.request().mode() == QaMode.RANKED) {
                var outcome = matrixGrounded(variant).execute(prepared.request().question(),
                        prepared.knowledge(), prepared.prompt());
                long elapsed = elapsedMs(startedAt);
                return matrixResponse(prepared, outcome.answer(), elapsed, outcome.usage(), outcome.failure(), outcome.trace(), variant);
            }
            ChatResponse response = matrixClient(variant).call(prepared.prompt());
            long elapsed = elapsedMs(startedAt);
            String answer = response.getResult().getOutput().getText();
            return matrixResponse(prepared, answer == null ? "" : answer, elapsed,
                    AnswerResponse.TokenUsage.from(response), null, null, variant);
        } catch (RuntimeException exception) {
            AiFailure failure = exception instanceof AiFailure known ? known : AiFailure.model(exception);
            if (!tolerateFailure) throw failure;
            return matrixResponse(prepared, "", elapsedMs(startedAt), null, failure, null, variant);
        }
    }

    private AnswerResponse matrixError(PreparedAnswer prepared, AiFailure failure, long generationMs) {
        String variant = prepared.provenance().model().equals(rankedModelSettings.baseModel()) ? "BASE" : "LORA";
        return matrixResponse(prepared, "", generationMs, null, failure, null, variant);
    }

    private AnswerResponse matrixResponse(PreparedAnswer prepared, String answer, long generationMs,
                                          AnswerResponse.TokenUsage usage, AiFailure failure,
                                          GroundedRankedAnswer.Trace grounding, String variant) {
        PythonKnowledgeResponse knowledge = prepared.knowledge();
        var provenance = prepared.provenance();
        if (grounding != null && grounding.generationSystemPromptSha256() != null) {
            provenance = new AnswerResponse.Provenance(provenance.runId(), provenance.comparisonId(),
                    provenance.evidenceId(), provenance.retrievalId(), provenance.evidenceStatus(),
                    provenance.promptSource(), provenance.promptTemplate(), provenance.promptSha256(),
                    provenance.systemPromptVersion(), grounding.generationSystemPromptSha256(), provenance.model(),
                    provenance.temperature(), provenance.maxTokens(), provenance.spaceId(), provenance.topK(),
                    provenance.returnedTripleOrder(), provenance.promptOrder(), provenance.scoresInPrompt(), provenance.rawOrderAvailable());
        }
        String method = prepared.request().mode().name();
        String key = "QWEN_" + variant + "_" + method;
        return new AnswerResponse(prepared.request().question(), prepared.request().mode(), answer,
                knowledge == null ? List.of() : knowledge.entities(),
                knowledge == null ? List.of() : promptStrategy.orderedTriples(prepared.request().mode(), knowledge.triples()),
                knowledge != null && knowledge.hasEvidence(), prepared.retrievalMs() + generationMs, prepared.prompt(),
                failure == null ? null : failure.getMessage(), provenance,
                new AnswerResponse.Timings(prepared.retrievalMs(), generationMs, prepared.retrievalMs() + generationMs),
                usage, failure == null ? null : failure.code(), grounding, "QWEN_MATRIX", variant, key);
    }

    private AnswerResponse execute(PreparedAnswer prepared, boolean tolerateFailure) {
        long startedAt = System.nanoTime();
        try {
            if ((prepared.provenance().comparisonId() != null && prepared.request().mode() == QaMode.RANKED)
                    || usesDedicatedRankedModel(prepared)) {
                GroundedRankedAnswer engine = usesDedicatedRankedModel(prepared) ? dedicatedGroundedRanked : groundedRanked;
                var outcome = engine.execute(prepared.request().question(), prepared.knowledge(), prepared.prompt());
                long generationMs = elapsedMs(startedAt);
                return response(prepared, outcome.answer(), prepared.retrievalMs() + generationMs,
                        generationMs, outcome.usage(), outcome.failure(), outcome.trace());
            }
            ChatResponse result = modelClient.call(prepared.prompt());
            long generationMs = elapsedMs(startedAt);
            String answer = result.getResult().getOutput().getText();
            return response(prepared, answer == null ? "" : answer, prepared.retrievalMs() + generationMs,
                    generationMs, AnswerResponse.TokenUsage.from(result), null);
        } catch (RuntimeException exception) {
            AiFailure failure = exception instanceof AiFailure known ? known : AiFailure.model(exception);
            if (!tolerateFailure) throw failure;
            long generationMs = elapsedMs(startedAt);
            return response(prepared, "", prepared.retrievalMs() + generationMs, generationMs, null, failure);
        }
    }

    private AnswerResponse response(PreparedAnswer prepared, String answer, long totalMs, long generationMs,
                                    AnswerResponse.TokenUsage usage, AiFailure failure) {
        return response(prepared, answer, totalMs, generationMs, usage, failure, null);
    }

    private AnswerResponse response(PreparedAnswer prepared, String answer, long totalMs, long generationMs,
                                    AnswerResponse.TokenUsage usage, AiFailure failure, GroundedRankedAnswer.Trace grounding) {
        PythonKnowledgeResponse knowledge = prepared.knowledge();
        var p = prepared.provenance();
        if (grounding != null && grounding.generationSystemPromptSha256() != null) {
            p = new AnswerResponse.Provenance(p.runId(), p.comparisonId(), p.evidenceId(), p.retrievalId(),
                    p.evidenceStatus(), p.promptSource(), p.promptTemplate(), p.promptSha256(),
                    p.systemPromptVersion(), grounding.generationSystemPromptSha256(), p.model(), p.temperature(),
                    p.maxTokens(), p.spaceId(), p.topK(), p.returnedTripleOrder(), p.promptOrder(), p.scoresInPrompt(), p.rawOrderAvailable());
        }
        return new AnswerResponse(prepared.request().question(), prepared.request().mode(), answer,
                knowledge == null ? List.of() : knowledge.entities(),
                knowledge == null ? List.of() : promptStrategy.orderedTriples(prepared.request().mode(), knowledge.triples()),
                knowledge != null && knowledge.hasEvidence(), totalMs, prepared.prompt(),
                failure == null ? null : failure.getMessage(), p,
                new AnswerResponse.Timings(prepared.retrievalMs(), generationMs, totalMs), usage,
                failure == null ? null : failure.code(), grounding);
    }

    static long elapsedMs(long startedAt) { return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000); }
    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Snapshot(PythonKnowledgeResponse knowledge, String evidenceId, String retrievalId, long retrievalMs,
                            AiFailure failure, int topK, boolean rawOrder) {
        static Snapshot direct() { return new Snapshot(null, null, null, 0, null, 0, false); }
    }
    public record PreparedAnswer(AnswerRequest request, String prompt, PythonKnowledgeResponse knowledge,
                                 AnswerResponse.Provenance provenance, long retrievalMs) { }
}
