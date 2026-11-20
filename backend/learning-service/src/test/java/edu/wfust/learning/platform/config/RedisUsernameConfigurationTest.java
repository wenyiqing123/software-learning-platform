package edu.wfust.learning.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisUsernameConfigurationTest {
    @Test
    void redisUsernameCanBeConfiguredForAcl() throws IOException {
        assertThat(redisUsername(Map.of("REDIS_USERNAME", "software_learning")))
                .isEqualTo("software_learning");
    }

    @Test
    void redisUsernameDefaultsToEmptyForPasswordOnlyConnections() throws IOException {
        assertThat(redisUsername(Map.of())).isEmpty();
    }

    private String redisUsername(Map<String, Object> variables) throws IOException {
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", variables));
        var yaml = new YamlPropertySourceLoader();
        environment.getPropertySources().addLast(yaml.load("application", new ClassPathResource("application.yml")).getFirst());
        return environment.getProperty("spring.data.redis.username");
    }
}
