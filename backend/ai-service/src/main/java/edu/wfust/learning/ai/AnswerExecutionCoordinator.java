package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

/** Owns model invocation, strategy dispatch and public response assembly. */
final class AnswerExecutionCoordinator {
    private final AiModelClient modelClient;
    private final PromptStrategy promptStrategy;
    private final GroundedRankedAnswer groundedRanked;
    private final QaStrategySelector strategySelector;
    private final AiModelClient dedicatedRankedModel;
    private final GroundedRankedAnswer dedicatedGroundedRanked;
    private final AiModelClient matrixBaseModelClient;
    private final AiModelClient matrixAdapterModelClient;
    private final GroundedRankedAnswer matrixBaseGroundedAnswer;
    private final GroundedRankedAnswer matrixAdapterGroundedAnswer;

    AnswerExecutionCoordinator(AiModelClient modelClient, PromptStrategy promptStrategy,
                               GroundedRankedAnswer groundedRanked, QaStrategySelector strategySelector,
                               AiModelClient dedicatedRankedModel, GroundedRankedAnswer dedicatedGroundedRanked,
                               AiModelClient matrixBaseModelClient, AiModelClient matrixAdapterModelClient,
                               GroundedRankedAnswer matrixBaseGroundedAnswer,
                               GroundedRankedAnswer matrixAdapterGroundedAnswer) {
        this.modelClient = modelClient;
        this.promptStrategy = promptStrategy;
        this.groundedRanked = groundedRanked;
        this.strategySelector = strategySelector;
        this.dedicatedRankedModel = dedicatedRankedModel;
        this.dedicatedGroundedRanked = dedicatedGroundedRanked;
        this.matrixBaseModelClient = matrixBaseModelClient;
        this.matrixAdapterModelClient = matrixAdapterModelClient;
        this.matrixBaseGroundedAnswer = matrixBaseGroundedAnswer;
        this.matrixAdapterGroundedAnswer = matrixAdapterGroundedAnswer;
    }

    boolean usesDedicatedRankedModel(AnswerService.PreparedAnswer prepared) {
        return dedicatedGroundedRanked != null && prepared.request().mode() == QaMode.RANKED
                && prepared.provenance().comparisonId() == null;
    }

    AnswerResponse execute(AnswerService.PreparedAnswer prepared, boolean tolerateFailure) {
        long startedAt = System.nanoTime();
        try {
            boolean dedicated = usesDedicatedRankedModel(prepared);
            AiModelClient generationModel = dedicated ? dedicatedRankedModel : modelClient;
            GroundedRankedAnswer generationGrounded = dedicated ? dedicatedGroundedRanked : groundedRanked;
            boolean grounded = (prepared.provenance().comparisonId() != null
                    && prepared.request().mode() == QaMode.RANKED) || dedicated;
            QaExecutionStrategy.Outcome outcome = strategySelector.select(prepared.request().mode(), grounded).execute(
                    new QaExecutionStrategy.Context(prepared.request().question(), prepared.knowledge(), prepared.prompt(),
                            generationModel, generationGrounded));
            long generationMs = AnswerService.elapsedMs(startedAt);
            return response(prepared, outcome.answer(), prepared.retrievalMs() + generationMs,
                    generationMs, outcome.usage(), outcome.failure(), outcome.grounding());
        } catch (RuntimeException exception) {
            AiFailure failure = exception instanceof AiFailure known ? known : AiFailure.model(exception);
            if (!tolerateFailure) throw failure;
            long generationMs = AnswerService.elapsedMs(startedAt);
            return response(prepared, "", prepared.retrievalMs() + generationMs, generationMs, null, failure, null);
        }
    }

    Flux<ChatResponse> stream(AnswerService.PreparedAnswer prepared) { return modelClient.stream(prepared.prompt()); }

    AnswerResponse completed(AnswerService.PreparedAnswer prepared, String answer, long totalMs,
                             long generationMs, AnswerResponse.TokenUsage usage) {
        return response(prepared, answer, totalMs, generationMs, usage, null, null);
    }

    AnswerResponse failure(AnswerService.PreparedAnswer prepared, AiFailure failure, long generationMs) {
        return response(prepared, "", prepared.retrievalMs() + generationMs, generationMs, null, failure, null);
    }

    AnswerResponse executeMatrix(AnswerService.PreparedAnswer prepared, String variant, boolean tolerateFailure) {
        long startedAt = System.nanoTime();
        try {
            if (prepared.request().mode() == QaMode.RANKED) {
                var outcome = matrixGrounded(variant).execute(prepared.request().question(), prepared.knowledge(), prepared.prompt());
                return matrixResponse(prepared, outcome.answer(), AnswerService.elapsedMs(startedAt), outcome.usage(), outcome.failure(), outcome.trace(), variant);
            }
            ChatResponse response = matrixClient(variant).call(prepared.prompt());
            String answer = response.getResult().getOutput().getText();
            return matrixResponse(prepared, answer == null ? "" : answer, AnswerService.elapsedMs(startedAt),
                    AnswerResponse.TokenUsage.from(response), null, null, variant);
        } catch (RuntimeException exception) {
            AiFailure failure = exception instanceof AiFailure known ? known : AiFailure.model(exception);
            if (!tolerateFailure) throw failure;
            return matrixResponse(prepared, "", AnswerService.elapsedMs(startedAt), null, failure, null, variant);
        }
    }

    AnswerResponse matrixError(AnswerService.PreparedAnswer prepared, AiFailure failure, long generationMs, String variant) {
        return matrixResponse(prepared, "", generationMs, null, failure, null, variant);
    }

    private AiModelClient matrixClient(String variant) { return "BASE".equals(variant) ? matrixBaseModelClient : matrixAdapterModelClient; }
    private GroundedRankedAnswer matrixGrounded(String variant) { return "BASE".equals(variant) ? matrixBaseGroundedAnswer : matrixAdapterGroundedAnswer; }

    private AnswerResponse response(AnswerService.PreparedAnswer prepared, String answer, long totalMs, long generationMs,
                                    AnswerResponse.TokenUsage usage, AiFailure failure, GroundedRankedAnswer.Trace grounding) {
        PythonKnowledgeResponse knowledge = prepared.knowledge();
        var provenance = withGenerationPromptHash(prepared.provenance(), grounding);
        return new AnswerResponse(prepared.request().question(), prepared.request().mode(), answer,
                knowledge == null ? List.of() : knowledge.entities(),
                knowledge == null ? List.of() : promptStrategy.orderedTriples(prepared.request().mode(), knowledge.triples()),
                knowledge != null && knowledge.hasEvidence(), totalMs, prepared.prompt(),
                failure == null ? null : failure.getMessage(), provenance,
                new AnswerResponse.Timings(prepared.retrievalMs(), generationMs, totalMs), usage,
                failure == null ? null : failure.code(), grounding);
    }

    private AnswerResponse matrixResponse(AnswerService.PreparedAnswer prepared, String answer, long generationMs,
                                          AnswerResponse.TokenUsage usage, AiFailure failure,
                                          GroundedRankedAnswer.Trace grounding, String variant) {
        PythonKnowledgeResponse knowledge = prepared.knowledge();
        var provenance = withGenerationPromptHash(prepared.provenance(), grounding);
        String method = prepared.request().mode().name();
        String key = "QWEN_" + variant + "_" + method;
        return new AnswerResponse(prepared.request().question(), prepared.request().mode(), answer,
                knowledge == null ? List.of() : knowledge.entities(),
                knowledge == null ? List.of() : promptStrategy.orderedTriples(prepared.request().mode(), knowledge.triples()),
                knowledge != null && knowledge.hasEvidence(), prepared.retrievalMs() + generationMs, prepared.prompt(),
                failure == null ? null : failure.getMessage(), provenance,
                new AnswerResponse.Timings(prepared.retrievalMs(), generationMs, prepared.retrievalMs() + generationMs), usage,
                failure == null ? null : failure.code(), grounding, "QWEN_MATRIX", variant, key);
    }

    private AnswerResponse.Provenance withGenerationPromptHash(AnswerResponse.Provenance provenance,
                                                               GroundedRankedAnswer.Trace grounding) {
        if (grounding == null || grounding.generationSystemPromptSha256() == null) return provenance;
        return new AnswerResponse.Provenance(provenance.runId(), provenance.comparisonId(), provenance.evidenceId(), provenance.retrievalId(),
                provenance.evidenceStatus(), provenance.promptSource(), provenance.promptTemplate(), provenance.promptSha256(),
                provenance.systemPromptVersion(), grounding.generationSystemPromptSha256(), provenance.model(), provenance.temperature(),
                provenance.maxTokens(), provenance.spaceId(), provenance.topK(), provenance.returnedTripleOrder(), provenance.promptOrder(),
                provenance.scoresInPrompt(), provenance.rawOrderAvailable());
    }
}
