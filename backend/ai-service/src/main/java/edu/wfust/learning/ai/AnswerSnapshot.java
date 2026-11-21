package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeResponse;

/** Immutable retrieval boundary shared by interactive, comparison and matrix flows. */
record AnswerSnapshot(PythonKnowledgeResponse knowledge, String evidenceId, String retrievalId, long retrievalMs,
                      AiFailure failure, int topK, boolean rawOrder) {
    static AnswerSnapshot direct() { return new AnswerSnapshot(null, null, null, 0, null, 0, false); }
}
