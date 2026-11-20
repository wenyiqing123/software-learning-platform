package edu.wfust.learning.ai;

import java.util.List;

public record TutorHintResponse(
        String question,
        int level,
        String content,
        boolean hasEvidence,
        List<HintEvidence> evidence,
        boolean nextLevelAvailable,
        String source) {
    public TutorHintResponse {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
    public record HintEvidence(String subject, String predicate, String object, double score) { }
}
