package edu.wfust.learning.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.ClassPathResource;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

class AiConfigurationTest {
    private Properties applicationProperties() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    @Test void yamlRequiresJwtSecretAndKeepsTheSharedEnvironmentNames() {
        Properties properties = applicationProperties();
        assertThat(properties.getProperty("security.jwt.secret")).isEqualTo("${JWT_SECRET}");
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testValues", java.util.Map.of(
                "JWT_SECRET", "test-jwt-secret-01234567890123456789", "PYTHON_KNOWLEDGE_URL", "http://127.0.0.1:18999",
                "OPENAI_BASE_URL", "https://provider.example/proxy/v1", "OPENAI_API_KEY", "test-key", "OPENAI_MODEL", "test-model")));
        assertThat(env.resolveRequiredPlaceholders(properties.getProperty("python.knowledge.base-url")))
                .isEqualTo("http://127.0.0.1:18999");
        assertThat(env.resolveRequiredPlaceholders(properties.getProperty("spring.ai.openai.base-url")))
                .isEqualTo("https://provider.example/proxy/v1");
    }

    @Test void applicationContextLoadsWithoutAModelKeyAndWithoutStartingAnyServer() {
        new WebApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        new org.springframework.boot.convert.ApplicationConversionService()))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AiServiceApplication.class)
                .withPropertyValues("JWT_SECRET=test-jwt-secret-01234567890123456789", "OPENAI_API_KEY=",
                        "OPENAI_BASE_URL=http://127.0.0.1:1/proxy/v1", "OPENAI_MODEL=test-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ModelSettings.class).configured()).isFalse();
                    assertThat(context).hasSingleBean(AiController.class);
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                });
    }

    @Test void applicationContextLoadsWithSeparateRankedModelEnabled() {
        new WebApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        new org.springframework.boot.convert.ApplicationConversionService()))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AiServiceApplication.class)
                .withPropertyValues(
                        "JWT_SECRET=test-jwt-secret-01234567890123456789",
                        "OPENAI_BASE_URL=https://provider.example/v1", "OPENAI_API_KEY=default-test-key",
                        "OPENAI_MODEL=default-model",
                        "RANKED_MODEL_ENABLED=true", "RANKED_MODEL_BASE_URL=http://127.0.0.1:8998/v1",
                        "RANKED_MODEL_API_KEY=ranked-test-key", "RANKED_MODEL_NAME=qwen35-ranked-lora")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AnswerService service = context.getBean(AnswerService.class);
                    assertThat(service.settings().model()).isEqualTo("default-model");
                    assertThat(service.rankedModelInfo()).containsEntry("rankedModelEnabled", true)
                            .containsEntry("rankedModelConfigured", true)
                            .containsEntry("rankedModel", "qwen35-ranked-lora");
                });
    }

    @Test void applicationContextLoadsWithQwenMatrixEnabled() {
        new WebApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory().setConversionService(
                        new org.springframework.boot.convert.ApplicationConversionService()))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AiServiceApplication.class)
                .withPropertyValues(
                        "JWT_SECRET=test-jwt-secret-01234567890123456789",
                        "OPENAI_BASE_URL=https://provider.example/v1", "OPENAI_API_KEY=default-test-key",
                        "OPENAI_MODEL=default-model",
                        "RANKED_MATRIX_ENABLED=true", "RANKED_MATRIX_BASE_MODEL_NAME=qwen35-base",
                        "RANKED_MODEL_BASE_URL=http://127.0.0.1:8998/v1",
                        "RANKED_MODEL_API_KEY=ranked-test-key", "RANKED_MODEL_NAME=qwen35-ranked-lora")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AnswerService service = context.getBean(AnswerService.class);
                    assertThat(service.rankedModelInfo()).containsEntry("qwenMatrixEnabled", true)
                            .containsEntry("qwenMatrixConfigured", true)
                            .containsEntry("qwenMatrixBaseModel", "qwen35-base")
                            .containsEntry("qwenMatrixAdapterModel", "qwen35-ranked-lora");
                });
    }
}
