package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;

import java.util.List;
import java.util.UUID;

/** Owns knowledge retrieval, prompt preparation and provenance construction. */
final class AnswerPreparationCoordinator {
    private final PythonKnowledgeClient knowledgeClient;
    private final PromptStrategy promptStrategy;
    private final AiModelClient defaultModel;
    private final AiModelClient dedicatedRankedModel;
    private final GroundedRankedAnswer dedicatedGroundedRanked;

    AnswerPreparationCoordinator(PythonKnowledgeClient knowledgeClient, PromptStrategy promptStrategy,
                                 AiModelClient defaultModel, AiModelClient dedicatedRankedModel,
                                 GroundedRankedAnswer dedicatedGroundedRanked) {
        this.knowledgeClient = knowledgeClient;
        this.promptStrategy = promptStrategy;
        this.defaultModel = defaultModel;
        this.dedicatedRankedModel = dedicatedRankedModel;
        this.dedicatedGroundedRanked = dedicatedGroundedRanked;
    }

    AnswerService.PreparedAnswer prepare(AnswerRequest request, AnswerSnapshot snapshot, String comparisonId) {
        if (request.mode().requiresRawOrder() && !snapshot.rawOrder()) throw AiFailure.ablationLimit();
        return prepared(request, promptStrategy.build(request.mode(), request.question(), snapshot.knowledge()), snapshot, comparisonId);
    }

    AnswerService.PreparedAnswer failurePrepared(AnswerRequest request, AnswerSnapshot snapshot, String comparisonId) {
        return prepared(request, "", snapshot, comparisonId);
    }

    AnswerSnapshot retrieveSnapshot(AnswerRequest request, boolean rawOrder) {
        String question = request.question();
        long startedAt = System.nanoTime();
        String retrievalId = UUID.randomUUID().toString();
        try {
            PythonKnowledgeResponse knowledge = rawOrder ? knowledgeClient.retrieveUntruncated(question)
                    : request.historyMessages().isEmpty() && request.historyEntities().isEmpty()
                    ? knowledgeClient.retrieve(question, List.of(), List.of())
                    : knowledgeClient.retrieve(question, List.of(), request.historyMessages(), request.historyEntities());
            if (knowledge == null) throw AiFailure.knowledge(null);
            return new AnswerSnapshot(knowledge, UUID.randomUUID().toString(), retrievalId,
                    AnswerService.elapsedMs(startedAt), null, rawOrder ? 0 : 20, rawOrder);
        } catch (RuntimeException exception) {
            AiFailure failure = exception instanceof AiFailure known ? known : AiFailure.knowledge(exception);
            return new AnswerSnapshot(null, null, retrievalId, AnswerService.elapsedMs(startedAt), failure,
                    rawOrder ? 0 : 20, rawOrder);
        }
    }

    AnswerSnapshot retrieveSnapshot(String question, boolean rawOrder) {
        return retrieveSnapshot(new AnswerRequest(question, QaMode.RANKED), rawOrder);
    }

    AnswerService.PreparedAnswer matrixPrepared(String question, AnswerSnapshot snapshot,
                                                String comparisonId, QaMode mode, String variant,
                                                AiModelClient matrixModel) {
        AnswerRequest request = new AnswerRequest(question, mode);
        String prompt = promptStrategy.build(mode, question, snapshot.knowledge());
        ModelSettings modelSettings = matrixModel.settings();
        String systemVersion = mode == QaMode.RANKED ? GroundedRankedAnswer.VERSION : null;
        String source = mode == QaMode.DIRECT ? "QUESTION"
                : mode == QaMode.RANKED ? "PYTHON_ORIGINAL_PLUS_MODEL_EVIDENCE_SELECTION" : "JAVA_TRIPLES_V1";
        String order = mode == QaMode.DIRECT ? "NONE"
                : mode == QaMode.RANKED ? "PYTHON_ORIGINAL" : "PYTHON_RETURNED";
        var provenance = new AnswerResponse.Provenance(UUID.randomUUID().toString(), comparisonId,
                snapshot.evidenceId(), snapshot.retrievalId(), mode == QaMode.DIRECT ? "NOT_REQUESTED"
                : snapshot.knowledge().hasEvidence() ? "AVAILABLE" : "EMPTY", source,
                snapshot.knowledge() == null ? null : snapshot.knowledge().promptTemplate(), AnswerService.sha256(prompt),
                systemVersion, null, modelSettings.model(), modelSettings.temperature(), modelSettings.maxTokens(),
                mode == QaMode.DIRECT ? null : 1, mode == QaMode.DIRECT ? null : snapshot.topK(),
                mode == QaMode.DIRECT ? "NONE" : snapshot.rawOrder() ? "PYTHON_RAW" : "PYTHON_RETURNED",
                order, mode == QaMode.RANKED, snapshot.rawOrder() && snapshot.knowledge() != null);
        return new AnswerService.PreparedAnswer(request, prompt, snapshot.knowledge(), provenance, snapshot.retrievalMs());
    }

    private AnswerService.PreparedAnswer prepared(AnswerRequest request, String prompt,
                                                   AnswerSnapshot snapshot, String comparisonId) {
        QaMode mode = request.mode();
        ModelSettings settings = defaultModel.settings();
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
                : snapshot.knowledge() != null && snapshot.knowledge().hasEvidence() ? "AVAILABLE" : "EMPTY";
        ModelSettings generationSettings = dedicatedRankedModel == null || !dedicatedRanked
                ? settings : dedicatedRankedModel.settings();
        var provenance = new AnswerResponse.Provenance(UUID.randomUUID().toString(), comparisonId,
                snapshot.evidenceId(), snapshot.retrievalId(), evidenceStatus, source,
                snapshot.knowledge() == null ? null : snapshot.knowledge().promptTemplate(), AnswerService.sha256(prompt),
                systemPromptVersion, null, generationSettings.model(), generationSettings.temperature(),
                generationSettings.maxTokens(), direct ? null : 1, direct ? null : snapshot.topK(),
                direct ? "NONE" : snapshot.rawOrder() ? "PYTHON_RAW" : "PYTHON_RETURNED", order,
                mode.injectsScores(), snapshot.rawOrder() && snapshot.knowledge() != null);
        return new AnswerService.PreparedAnswer(request, prompt, snapshot.knowledge(), provenance,
                mode == QaMode.DIRECT ? 0 : snapshot.retrievalMs());
    }
}
