package edu.wfust.learning.ai.client;

import java.util.List;

public record PythonKnowledgeRequest(String question, List<String> entities, List<String> historyEntities,
                                     List<String> historyMessages, String relation, Integer spaceId, Integer topK) { }
