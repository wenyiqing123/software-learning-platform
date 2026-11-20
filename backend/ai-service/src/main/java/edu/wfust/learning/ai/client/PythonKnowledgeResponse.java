package edu.wfust.learning.ai.client;

import java.util.List;

public record PythonKnowledgeResponse(String question, List<PythonEntity> entities, List<PythonTriple> triples,
                                      String prompt, boolean hasEvidence, String promptTemplate) {
    public PythonKnowledgeResponse {
        entities = entities == null ? List.of() : List.copyOf(entities);
        triples = triples == null ? List.of() : List.copyOf(triples);
    }
}
