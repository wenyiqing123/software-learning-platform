package edu.wfust.learning.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class RankedModelConfiguration {
    @Bean
    RankedModelSettings rankedModelSettings(
            @Value("${ranked.model.enabled:false}") boolean enabled,
            @Value("${ranked.model.base-url:http://127.0.0.1:8998/v1}") String baseUrl,
            @Value("${ranked.model.api-key:}") String apiKey,
            @Value("${ranked.model.name:qwen35-ranked-lora}") String model,
            @Value("${ranked.matrix.base-model-name:qwen35-base}") String baseModel,
            @Value("${ranked.model.temperature:0}") double temperature,
            @Value("${ranked.model.max-tokens:512}") int maxTokens,
            @Value("${ranked.model.http-timeout:90s}") Duration timeout,
            @Value("${ranked.matrix.enabled:false}") boolean matrixEnabled) {
        return new RankedModelSettings(enabled,baseUrl,apiKey,model,baseModel,temperature,maxTokens,timeout,matrixEnabled);
    }

    @Bean(name = "matrixBaseModelClient")
    @ConditionalOnProperty(prefix = "ranked.matrix", name = "enabled", havingValue = "true")
    AiModelClient matrixBaseModelClient(RankedModelSettings settings) {
        settings.requireMatrixConfigured();
        return new AiModelClient(settings.baseModelSettings());
    }

    @Bean(name = "rankedModelClient")
    @ConditionalOnProperty(prefix = "ranked.model", name = "enabled", havingValue = "true")
    AiModelClient rankedModelClient(RankedModelSettings settings) {
        settings.requireConfigured();
        return new AiModelClient(settings.modelSettings());
    }

    @Bean(name = "matrixAdapterModelClient")
    @ConditionalOnProperty(prefix = "ranked.matrix", name = "enabled", havingValue = "true")
    AiModelClient matrixAdapterModelClient(RankedModelSettings settings) {
        settings.requireMatrixConfigured();
        return new AiModelClient(settings.modelSettings());
    }

    @Bean(name = "matrixAdapterGroundedRankedAnswer")
    @ConditionalOnProperty(prefix = "ranked.matrix", name = "enabled", havingValue = "true")
    GroundedRankedAnswer matrixAdapterGroundedRankedAnswer(
            @Qualifier("matrixAdapterModelClient") AiModelClient model, ObjectMapper json) {
        return new GroundedRankedAnswer(model, json);
    }

    @Bean(name = "matrixBaseGroundedRankedAnswer")
    @ConditionalOnProperty(prefix = "ranked.matrix", name = "enabled", havingValue = "true")
    GroundedRankedAnswer matrixBaseGroundedRankedAnswer(
            @Qualifier("matrixBaseModelClient") AiModelClient model, ObjectMapper json) {
        return new GroundedRankedAnswer(model, json);
    }

    @Bean(name = "rankedGroundedRankedAnswer")
    @ConditionalOnBean(name = "rankedModelClient")
    GroundedRankedAnswer rankedGroundedRankedAnswer(
            @Qualifier("rankedModelClient") AiModelClient model, ObjectMapper json) {
        return new GroundedRankedAnswer(model, json);
    }
}

