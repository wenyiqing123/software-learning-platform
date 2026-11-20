package edu.wfust.learning.platform.messaging;

import java.util.UUID;

public record LearningEvent(UUID eventId, Long userId, Long knowledgePointId, Boolean correct, Long attemptId) { }
