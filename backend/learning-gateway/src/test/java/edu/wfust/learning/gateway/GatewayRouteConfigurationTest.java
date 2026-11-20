package edu.wfust.learning.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRouteConfigurationTest {
    @Test
    void routesUseContainerServiceUrlsWhenConfigured() throws IOException {
        var routes = routes(Map.of(
                "AI_SERVICE_URL", "http://ai-service:8082",
                "LEARNING_SERVICE_URL", "http://learning-service:8081"));

        assertThat(route(routes, "ai-service").getUri()).hasToString("http://ai-service:8082");
        assertThat(route(routes, "learning-service").getUri()).hasToString("http://learning-service:8081");
    }

    @Test
    void routesKeepLoopbackDefaultsForLocalDevelopment() throws IOException {
        var routes = routes(Map.of());

        assertThat(route(routes, "ai-service").getUri()).hasToString("http://127.0.0.1:8082");
        assertThat(route(routes, "learning-service").getUri()).hasToString("http://127.0.0.1:8081");
    }

    private List<RouteDefinition> routes(Map<String, Object> variables) throws IOException {
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", variables));
        var yaml = new YamlPropertySourceLoader();
        environment.getPropertySources().addLast(yaml.load("application", new ClassPathResource("application.yml")).getFirst());
        return Binder.get(environment)
                .bind("spring.cloud.gateway.server.webflux.routes", Bindable.listOf(RouteDefinition.class))
                .get();
    }

    private RouteDefinition route(List<RouteDefinition> routes, String id) {
        return routes.stream().filter(candidate -> id.equals(candidate.getId())).findFirst().orElseThrow();
    }
}
