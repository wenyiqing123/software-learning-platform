package edu.wfust.learning.platform.config;

import edu.wfust.learning.platform.messaging.LearningEventPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RabbitConfig {
    public static final String QUEUE = "slp.learning.events.quiz";
    public static final String DEAD_EXCHANGE = "slp.learning.events.dlx";
    public static final String DEAD_QUEUE = "slp.learning.events.dead";

    @Bean DirectExchange learningExchange() { return new DirectExchange(LearningEventPublisher.EXCHANGE, true, false); }
    @Bean DirectExchange learningDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }
    @Bean Queue learningEventQueue() { return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey("dead").build(); }
    @Bean Queue learningDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding learningEventBinding() { return BindingBuilder.bind(learningEventQueue()).to(learningExchange()).with(LearningEventPublisher.ROUTING_KEY); }
    @Bean Binding learningDeadBinding() { return BindingBuilder.bind(learningDeadQueue()).to(learningDeadExchange()).with("dead"); }
}
