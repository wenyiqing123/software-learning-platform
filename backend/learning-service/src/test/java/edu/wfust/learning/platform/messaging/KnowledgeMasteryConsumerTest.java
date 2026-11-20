package edu.wfust.learning.platform.messaging;

import edu.wfust.learning.platform.entity.LearningEventEntity;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.LearningEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class KnowledgeMasteryConsumerTest {
    @Test
    void unknownEventRowIsRejectedSoTheBrokerCanRetryOrDeadLetterIt() {
        var events = mock(LearningEventMapper.class);
        when(events.findByEventId(anyString())).thenReturn(null);
        var consumer = new KnowledgeMasteryConsumer(new ObjectMapper(), events,
                mock(KnowledgeMasteryMapper.class), mock(StringRedisTemplate.class));

        String payload = """
                {"eventId":"%s","userId":9,"knowledgePointId":4,"correct":true,"attemptId":11}
                """.formatted(UUID.randomUUID());
        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox");
    }

    @Test
    void processedEventIsIdempotentAndDoesNotRecordMasteryAgain() throws Exception {
        var events = mock(LearningEventMapper.class);
        var stored = new LearningEventEntity();
        stored.setProcessedAt(LocalDateTime.now());
        when(events.findByEventId(anyString())).thenReturn(stored);
        var mastery = mock(KnowledgeMasteryMapper.class);
        var consumer = new KnowledgeMasteryConsumer(new ObjectMapper(), events, mastery, mock(StringRedisTemplate.class));

        String payload = """
                {"eventId":"%s","userId":9,"knowledgePointId":4,"correct":true,"attemptId":11}
                """.formatted(UUID.randomUUID());
        consumer.consume(payload);
        verifyNoInteractions(mastery);
    }
}

