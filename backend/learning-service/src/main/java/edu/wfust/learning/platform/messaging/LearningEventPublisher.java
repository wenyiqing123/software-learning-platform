package edu.wfust.learning.platform.messaging;

import tools.jackson.databind.ObjectMapper;
import edu.wfust.learning.platform.entity.LearningEventEntity;
import edu.wfust.learning.platform.mapper.LearningEventMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LearningEventPublisher {
    public static final String EXCHANGE = "slp.learning.events";
    public static final String ROUTING_KEY = "quiz.attempt.submitted";
    private final LearningEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    public LearningEventPublisher(LearningEventMapper eventMapper, ObjectMapper objectMapper) {
        this.eventMapper = eventMapper;
        this.objectMapper = objectMapper;
    }

    public void publishAfterCommit(Long userId, Long knowledgePointId, boolean correct, Long attemptId) {
        LearningEvent event = new LearningEvent(UUID.randomUUID(), userId, knowledgePointId, correct, attemptId);
        LearningEventEntity row = new LearningEventEntity();
        row.setEventId(event.eventId().toString());
        row.setUserId(userId);
        row.setEventType(ROUTING_KEY);
        try {
            row.setPayloadJson(objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成学习事件", exception);
        }
        eventMapper.insert(row);
    }
}
