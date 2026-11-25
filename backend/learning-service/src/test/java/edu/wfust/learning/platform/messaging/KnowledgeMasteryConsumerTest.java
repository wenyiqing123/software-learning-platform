package edu.wfust.learning.platform.messaging;

import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.entity.LearningEventEntity;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.LearningEventMapper;
import edu.wfust.learning.platform.mapper.RecommendationSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
                mock(KnowledgeMasteryMapper.class), mock(StringRedisTemplate.class), mock(RecommendationSnapshotMapper.class));

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
        var consumer = new KnowledgeMasteryConsumer(new ObjectMapper(), events, mastery, mock(StringRedisTemplate.class), mock(RecommendationSnapshotMapper.class));

        String payload = """
                {"eventId":"%s","userId":9,"knowledgePointId":4,"correct":true,"attemptId":11}
                """.formatted(UUID.randomUUID());
        consumer.consume(payload);
        verifyNoInteractions(mastery);
    }

    @Test
    void successfulEventInvalidatesRedisAndRecommendationSnapshotAfterCommitRegistration() throws Exception {
        var events = mock(LearningEventMapper.class);
        var stored = new LearningEventEntity();
        stored.setId(7L);
        when(events.findByEventId(anyString())).thenReturn(stored);
        var mastery = mock(KnowledgeMasteryMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var snapshots = mock(RecommendationSnapshotMapper.class);
        var consumer = new KnowledgeMasteryConsumer(new ObjectMapper(), events, mastery, redis, snapshots);

        String payload = """
                {"eventId":"%s","userId":9,"knowledgePointId":4,"correct":true,"attemptId":11}
                """.formatted(UUID.randomUUID());
        consumer.consume(payload);

        verify(mastery).recordAttempt(9L, 4L, true);
        verify(redis).delete("slp:recommendation:9");
        verify(snapshots).deleteForUser(9L);
    }

    @Test
    void enforcesMonotonicPeakMasteryWhenNewScoreExceedsExistingPeak() throws Exception {
        var events = mock(LearningEventMapper.class);
        var stored = new LearningEventEntity();
        stored.setId(8L);
        when(events.findByEventId(anyString())).thenReturn(stored);

        var entity = new KnowledgeMasteryEntity();
        entity.setId(15L);
        entity.setUserId(9L);
        entity.setKnowledgePointId(4L);
        entity.setMasteryScore(new BigDecimal("88.00"));
        entity.setPeakMastery(new BigDecimal("80.00"));

        var mastery = mock(KnowledgeMasteryMapper.class);
        when(mastery.selectOne(any())).thenReturn(entity);

        var consumer = new KnowledgeMasteryConsumer(new ObjectMapper(), events, mastery, mock(StringRedisTemplate.class), mock(RecommendationSnapshotMapper.class));
        String payload = """
                {"eventId":"%s","userId":9,"knowledgePointId":4,"correct":true,"attemptId":12}
                """.formatted(UUID.randomUUID());
        consumer.consume(payload);

        verify(mastery).updateById(argThat((KnowledgeMasteryEntity updated) ->
                updated.getPeakMastery().compareTo(new BigDecimal("88.00")) == 0));
    }
}

