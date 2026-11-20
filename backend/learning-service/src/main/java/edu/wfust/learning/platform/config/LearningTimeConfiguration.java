package edu.wfust.learning.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
public class LearningTimeConfiguration {
    @Bean
    Clock learningClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }
}
