package edu.wfust.learning.ai;

import java.util.List;

/**
 * 费曼“以教促学”自检白名单响应 DTO：绝不包含任何后台系统提示词或模型内部链路字段。
 */
public record FeynmanEvaluateResponse(
        String knowledgePoint,
        List<String> coveredPoints,
        List<TripleEvidence> missingTriples,
        List<String> misconceptions,
        String followUpQuestion,
        List<TripleEvidence> evidenceTriples
) {
    public record TripleEvidence(String subject, String predicate, String object, double score) {
    }
}
