package edu.wfust.learning.platform.messaging;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import edu.wfust.learning.platform.config.RabbitConfig;
import edu.wfust.learning.platform.entity.LearningEventEntity;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.LearningEventMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Component
public class KnowledgeMasteryConsumer {
    private final ObjectMapper objectMapper;
    private final LearningEventMapper eventMapper;
    private final KnowledgeMasteryMapper masteryMapper;
    private final StringRedisTemplate redis;

    public KnowledgeMasteryConsumer(ObjectMapper objectMapper, LearningEventMapper eventMapper,
                                    KnowledgeMasteryMapper masteryMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.eventMapper = eventMapper;
        this.masteryMapper = masteryMapper;
        this.redis = redis;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @Transactional
    public void consume(String payload) throws Exception {
        LearningEvent event = objectMapper.readValue(payload, LearningEvent.class);
        LearningEventEntity stored = eventMapper.findByEventId(event.eventId().toString());
        if (stored == null || stored.getProcessedAt() != null) return;
        if (event.knowledgePointId() != null) {
            masteryMapper.recordAttempt(event.userId(), event.knowledgePointId(), Boolean.TRUE.equals(event.correct()));
        }
        eventMapper.update(null, new UpdateWrapper<LearningEventEntity>().eq("id", stored.getId()).set("processed_at", LocalDateTime.now()));
        Runnable invalidate = () -> { try { redis.delete("slp:recommendation:" + event.userId()); } catch (RuntimeException ignored) { /* Redis TTL bounds stale cache when unavailable. */ } };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { invalidate.run(); }
            });
        } else invalidate.run();
    }
}
