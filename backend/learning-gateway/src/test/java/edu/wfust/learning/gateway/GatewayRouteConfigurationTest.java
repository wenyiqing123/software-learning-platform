package edu.wfust.learning.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

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

    @Test
    void coreApiPrefixesAreCoveredByTheIntendedGatewayRoute() throws IOException {
        var routeDefinitions = routes(Map.of());
        var paths = List.of(
                "/api/auth/login",
                "/api/courses/1/study",
                "/api/knowledge/graph",
                "/api/system/status",
                "/api/evaluations/sessions",
                "/api/admin/evaluations/summary",
                "/api/experiments/export",
                "/api/experiment-tasks",
                "/api/admin/experiment-tasks",
                "/api/admin/question-bank/versions",
                "/api/admin/micro-lessons",
                "/api/admin/questions/1/diagnostics",
                "/api/admin/teaching-dashboard",
                "/api/admin/users",
                "/api/quizzes/1/submit",
                "/api/spaced-reviews/due",
                "/api/study-plans/today",
                "/api/knowledge/learning-path",
                "/api/knowledge/mastery-map",
                "/api/recommendations",
                "/api/reports/learning");

        for (String path : paths) {
            assertThat(matchingRoute(routeDefinitions, path).getId())
                    .as("gateway route for %s", path)
                    .isEqualTo("learning-service");
        }
    }

    @Test
    void aiRoutesCoverAllModelAndSseEndpointsBeforeTheLearningFallback() throws IOException {
        var routeDefinitions = routes(Map.of());
        var ai = route(routeDefinitions, "ai-service");
        assertThat(ai.getOrder()).isEqualTo(-1);
        assertThat(matchingRoute(routeDefinitions, "/api/ai/ask").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/compare").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/compare/qwen-matrix").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/ask/stream").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/micro-lesson").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/tutor/hint").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/feynman/evaluate").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/dependencies").getId()).isEqualTo("ai-service");
        assertThat(matchingRoute(routeDefinitions, "/api/ai/matrix-health").getId()).isEqualTo("ai-service");
    }

    @Test
    void gatewayTimeoutIsLongerThanTheAiSseContract() throws IOException {
        var environment = new MockEnvironment();
        var yaml = new YamlPropertySourceLoader();
        environment.getPropertySources().addLast(yaml.load("application", new ClassPathResource("application.yml")).getFirst());

        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.httpclient.connect-timeout")).isEqualTo("3000");
        assertThat(environment.getProperty("spring.cloud.gateway.server.webflux.httpclient.response-timeout")).isEqualTo("210s");
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

    private RouteDefinition matchingRoute(List<RouteDefinition> routes, String path) {
        return routes.stream().filter(candidate -> candidate.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .map(PathPatternParser.defaultInstance::parse)
                .anyMatch(pattern -> pattern.matches(PathContainer.parsePath(path))))
                .findFirst().orElseThrow(() -> new AssertionError("No gateway route matches " + path));
    }
}
