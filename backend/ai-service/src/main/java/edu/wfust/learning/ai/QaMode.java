package edu.wfust.learning.ai;

public enum QaMode {
    DIRECT, TRIPLES, RANKED,
    ABLATION_SORTED_SCORED, ABLATION_WITHOUT_SCORE, ABLATION_WITHOUT_SORT, ABLATION_WITHOUT_SORT_AND_SCORE;

    public boolean experimentOnly() { return this != DIRECT && this != TRIPLES && this != RANKED; }
    public boolean requiresRawOrder() { return this == ABLATION_WITHOUT_SORT || this == ABLATION_WITHOUT_SORT_AND_SCORE; }
    public boolean injectsScores() { return this == RANKED || this == ABLATION_SORTED_SCORED || this == ABLATION_WITHOUT_SORT; }
}
