package edu.wfust.learning.platform.messaging;
import edu.wfust.learning.platform.entity.LearningEventEntity;
import edu.wfust.learning.platform.mapper.LearningEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OutboxRelayTest {
    @Test void brokerRejectLeavesOutboxPendingForRetry(){
        var mapper=mock(LearningEventMapper.class);var rabbit=mock(RabbitTemplate.class);
        var event=new LearningEventEntity();event.setId(1L);event.setEventId("test-event");event.setEventType(LearningEventPublisher.ROUTING_KEY);event.setPayloadJson("{}");
        when(mapper.findPending()).thenReturn(List.of(event));
        doAnswer(inv->{((CorrelationData)inv.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(false,"broker nack"));return null;}).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(CorrelationData.class));
        new OutboxRelay(mapper,rabbit).relayPending();
        verify(mapper,never()).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void unroutableConfirmedMessageStaysPendingForTheNextRelay() {
        var mapper=mock(LearningEventMapper.class);var rabbit=mock(RabbitTemplate.class);
        var event=new LearningEventEntity();event.setId(2L);event.setEventId("returned-event");event.setEventType(LearningEventPublisher.ROUTING_KEY);event.setPayloadJson("{}");
        when(mapper.findPending()).thenReturn(List.of(event));
        doAnswer(inv->{var correlation=(CorrelationData)inv.getArgument(3);correlation.setReturned(new ReturnedMessage(new Message(new byte[0]),312,"NO_ROUTE","exchange","key"));correlation.getFuture().complete(new CorrelationData.Confirm(true,"ack"));return null;}).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class),any(CorrelationData.class));
        new OutboxRelay(mapper,rabbit).relayPending();
        verify(mapper,never()).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
}
