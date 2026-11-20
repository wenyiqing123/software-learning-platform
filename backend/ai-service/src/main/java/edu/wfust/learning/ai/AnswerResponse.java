package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonEntity;
import edu.wfust.learning.ai.client.PythonTriple;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatResponse;
import java.util.List;

public record AnswerResponse(String question, QaMode mode, String answer, List<PythonEntity> entities,
                             List<PythonTriple> triples, boolean hasEvidence, long latencyMs,
                             String prompt, String error, Provenance provenance, Timings timings,
                             TokenUsage usage, String errorCode, GroundedRankedAnswer.Trace grounding,
                             String experimentType, String modelVariant, String resultKey) {
    public AnswerResponse {
        entities = entities == null ? List.of() : List.copyOf(entities);
        triples = triples == null ? List.of() : List.copyOf(triples);
    }

    public AnswerResponse(String question, QaMode mode, String answer, List<PythonEntity> entities,
                          List<PythonTriple> triples, boolean hasEvidence, long latencyMs,
                          String prompt, String error, Provenance provenance, Timings timings,
                          TokenUsage usage, String errorCode, GroundedRankedAnswer.Trace grounding) {
        this(question, mode, answer, entities, triples, hasEvidence, latencyMs, prompt, error, provenance,
                timings, usage, errorCode, grounding, null, null, mode == null ? null : mode.name());
    }

    public record Provenance(String runId, String comparisonId, String evidenceId, String retrievalId,
                             String evidenceStatus, String promptSource, String promptTemplate, String promptSha256,
                             String systemPromptVersion, String systemPromptSha256,
                             String model, double temperature, int maxTokens, Integer spaceId, Integer topK,
                             String returnedTripleOrder, String promptOrder, boolean scoresInPrompt,
                             boolean rawOrderAvailable) { }
    public record Timings(long retrievalMs, long generationMs, long totalMs) { }
    public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        public static TokenUsage from(ChatResponse response) {
            if (response == null || response.getMetadata() == null) return null;
            var usage = response.getMetadata().getUsage();
            if (usage == null || usage instanceof EmptyUsage) return null;
            if (usage.getPromptTokens() == null && usage.getCompletionTokens() == null && usage.getTotalTokens() == null) return null;
            return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }
    }
}
