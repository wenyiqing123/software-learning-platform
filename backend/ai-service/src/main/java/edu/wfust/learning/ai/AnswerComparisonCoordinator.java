package edu.wfust.learning.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Owns bounded parallel comparison and the six-arm Qwen matrix orchestration. */
final class AnswerComparisonCoordinator {
    private static final List<QaMode> PRIMARY = List.of(QaMode.DIRECT, QaMode.TRIPLES, QaMode.RANKED);
    private static final List<QaMode> ABLATIONS = List.of(QaMode.ABLATION_SORTED_SCORED, QaMode.ABLATION_WITHOUT_SCORE,
            QaMode.ABLATION_WITHOUT_SORT, QaMode.ABLATION_WITHOUT_SORT_AND_SCORE);
    private final ComparisonExecutor comparisons;
    private final AnswerPreparationCoordinator preparation;
    private final AnswerExecutionCoordinator execution;
    private final RankedModelSettings rankedModelSettings;
    private final AiModelClient matrixBaseModelClient;
    private final AiModelClient matrixAdapterModelClient;
    private final GroundedRankedAnswer matrixBaseGroundedAnswer;
    private final GroundedRankedAnswer matrixAdapterGroundedAnswer;

    AnswerComparisonCoordinator(ComparisonExecutor comparisons, AnswerPreparationCoordinator preparation,
                                AnswerExecutionCoordinator execution, RankedModelSettings rankedModelSettings,
                                AiModelClient matrixBaseModelClient, AiModelClient matrixAdapterModelClient,
                                GroundedRankedAnswer matrixBaseGroundedAnswer,
                                GroundedRankedAnswer matrixAdapterGroundedAnswer) {
        this.comparisons = comparisons;
        this.preparation = preparation;
        this.execution = execution;
        this.rankedModelSettings = rankedModelSettings;
        this.matrixBaseModelClient = matrixBaseModelClient;
        this.matrixAdapterModelClient = matrixAdapterModelClient;
        this.matrixBaseGroundedAnswer = matrixBaseGroundedAnswer;
        this.matrixAdapterGroundedAnswer = matrixAdapterGroundedAnswer;
    }

    List<AnswerResponse> compare(String question, boolean includeAblations, Runnable requireConfigured) {
        requireConfigured.run();
        long deadline = comparisons.deadline();
        String comparisonId = UUID.randomUUID().toString();
        List<PendingAnswer> pending = new ArrayList<>();
        Future<AnswerSnapshot> retrieval = null;
        try {
            pending.add(submit(preparation.prepare(new AnswerRequest(question, QaMode.DIRECT), AnswerSnapshot.direct(), comparisonId), deadline));
            AnswerSnapshot snapshot;
            long retrievalStarted = System.nanoTime();
            try {
                retrieval = comparisons.submit(() -> preparation.retrieveSnapshot(question, includeAblations));
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
                    pending.add(new PendingAnswer(preparation.failurePrepared(request, snapshot, comparisonId), null, snapshot.failure(), System.nanoTime()));
                    continue;
                }
                try {
                    pending.add(submit(preparation.prepare(request, snapshot, comparisonId), deadline));
                } catch (AiFailure failure) {
                    pending.add(new PendingAnswer(preparation.failurePrepared(request, snapshot, comparisonId), null, failure, System.nanoTime()));
                }
            }
            return pending.stream().map(item -> await(item, deadline)).toList();
        } finally {
            comparisons.cancel(retrieval);
            pending.forEach(item -> comparisons.cancel(item.future()));
        }
    }

    List<AnswerResponse> compareQwenMatrix(String question) {
        if (rankedModelSettings == null) throw matrixUnavailable();
        rankedModelSettings.requireMatrixConfigured();
        if (matrixBaseModelClient == null || matrixAdapterModelClient == null
                || matrixBaseGroundedAnswer == null || matrixAdapterGroundedAnswer == null) throw matrixUnavailable();
        requireMatrixModel(matrixBaseModelClient, rankedModelSettings.baseModel());
        requireMatrixModel(matrixAdapterModelClient, rankedModelSettings.modelSettings().model());
        long deadline = comparisons.deadline();
        String comparisonId = UUID.randomUUID().toString();
        AnswerSnapshot snapshot = preparation.retrieveSnapshot(question, false);
        List<AnswerResponse> results = new ArrayList<>(6);
        for (String variant : List.of("BASE", "LORA")) {
            AiModelClient model = "BASE".equals(variant) ? matrixBaseModelClient : matrixAdapterModelClient;
            for (QaMode mode : PRIMARY) {
                AnswerRequest request = new AnswerRequest(question, mode);
                AnswerService.PreparedAnswer prepared = mode != QaMode.DIRECT && snapshot.failure() != null
                        ? preparation.failurePrepared(request, snapshot, comparisonId)
                        : preparation.matrixPrepared(question, snapshot, comparisonId, mode, variant, model);
                if (mode != QaMode.DIRECT && snapshot.failure() != null) {
                    results.add(execution.matrixError(prepared, snapshot.failure(), 0, variant));
                    continue;
                }
                if (comparisons.expired(deadline)) {
                    results.add(execution.matrixError(prepared, AiFailure.comparisonTimeout(), 0, variant));
                    continue;
                }
                long started = System.nanoTime();
                try {
                    results.add(comparisons.submit(() -> execution.executeMatrix(prepared, variant, true))
                            .get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
                } catch (TimeoutException exception) {
                    results.add(execution.matrixError(prepared, AiFailure.comparisonTimeout(), AnswerService.elapsedMs(started), variant));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    results.add(execution.matrixError(prepared, AiFailure.comparisonTimeout(), AnswerService.elapsedMs(started), variant));
                } catch (ExecutionException exception) {
                    AiFailure failure = exception.getCause() instanceof AiFailure known ? known : AiFailure.model(exception.getCause());
                    results.add(execution.matrixError(prepared, failure, AnswerService.elapsedMs(started), variant));
                }
            }
        }
        return List.copyOf(results);
    }

    private PendingAnswer submit(AnswerService.PreparedAnswer prepared, long deadline) {
        long startedAt = System.nanoTime();
        if (comparisons.expired(deadline)) return new PendingAnswer(prepared, null, AiFailure.comparisonTimeout(), startedAt);
        try { return new PendingAnswer(prepared, comparisons.submit(() -> execution.execute(prepared, true)), null, startedAt); }
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
        long generationMs = pending.future() == null ? 0 : AnswerService.elapsedMs(pending.startedAt());
        return execution.failure(pending.prepared(), failure, generationMs);
    }

    private AnswerSnapshot failedSnapshot(boolean rawOrder, long startedAt, AiFailure failure) {
        return new AnswerSnapshot(null, null, UUID.randomUUID().toString(), AnswerService.elapsedMs(startedAt), failure,
                rawOrder ? 0 : 20, rawOrder);
    }

    private void requireMatrixModel(AiModelClient client, String expectedModel) {
        if (!client.settings().model().equals(expectedModel)) throw matrixUnavailable();
        client.requireConfigured();
    }

    private AiFailure matrixUnavailable() {
        return new AiFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "QWEN_MATRIX_UNAVAILABLE", "本地 Qwen 基座/LoRA 对照配置暂不可用。", false);
    }

    private record PendingAnswer(AnswerService.PreparedAnswer prepared, Future<AnswerResponse> future,
                                 AiFailure failure, long startedAt) { }
}
