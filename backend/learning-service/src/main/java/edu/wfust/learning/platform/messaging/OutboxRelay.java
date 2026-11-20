package edu.wfust.learning.platform.messaging;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import edu.wfust.learning.platform.entity.LearningEventEntity;
import edu.wfust.learning.platform.mapper.LearningEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final LearningEventMapper eventMapper;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(LearningEventMapper eventMapper, RabbitTemplate rabbitTemplate) {
        this.eventMapper = eventMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${OUTBOX_RELAY_DELAY_MS:3000}")
    public void relayPending() {
        for (LearningEventEntity event : eventMapper.findPending()) {
            try {
                var correlation = new org.springframework.amqp.rabbit.connection.CorrelationData(event.getEventId());
                rabbitTemplate.convertAndSend(LearningEventPublisher.EXCHANGE, event.getEventType(), event.getPayloadJson(), correlation);
                var confirmation = correlation.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!confirmation.ack() || correlation.getReturned() != null) {
                    log.warn("Outbox delivery not confirmed for event {}", event.getEventId());
                    return;
                }
                eventMapper.update(null, new UpdateWrapper<LearningEventEntity>().eq("id", event.getId()).isNull("published_at")
                        .set("published_at", LocalDateTime.now()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.warn("Outbox delivery deferred for event {}", event.getEventId());
                return;
            }
        }
    }
}
