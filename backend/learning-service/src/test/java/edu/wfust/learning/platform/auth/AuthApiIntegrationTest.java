package edu.wfust.learning.platform.auth;

import edu.wfust.learning.platform.knowledge.PythonGraphClient;
import edu.wfust.learning.platform.mapper.SysUserMapper;
import edu.wfust.learning.platform.messaging.OutboxRelay;
import edu.wfust.learning.platform.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:auth-test-schema.sql",
    "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false",
    "security.jwt.secret=integration-test-secret-at-least-32-bytes", "security.jwt.expiration-minutes=37"
})
class AuthApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired SysUserMapper users;
    @Autowired RefreshTokenMapper refreshTokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @MockitoBean OutboxRelay relay;
    @MockitoBean PythonGraphClient graph;
    final HttpClient http = HttpClient.newHttpClient();

    @Test void registrationStoresBcryptAndReturnsConfiguredTokenLifetime() throws Exception {
        String username = username();
        var response = post("/api/auth/register", Map.of("username", username, "password", "study-password", "displayName", "学习者"), null);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json.readTree(response.body());
        var user = users.findByUsername(username);
        assertThat(encoder.matches("study-password", user.getPasswordHash())).isTrue();
        var claims = jwt.parse(body.path("accessToken").asText());
        long actualSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(body.path("expiresIn").asLong()).isEqualTo(actualSeconds).isEqualTo(37 * 60);
        var stored = refreshTokens.selectList(null).stream().filter(t -> t.getUserId().equals(user.getId())).findFirst().orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(body.path("refreshToken").asText());
    }

    @Test void refreshRotatesOnceAndLogoutRevokesReplacement() throws Exception {
        var initial = register();
        var rotated = post("/api/auth/refresh", Map.of("refreshToken", initial.path("refreshToken").asText()), null);
        assertThat(rotated.statusCode()).isEqualTo(200);
        JsonNode next = json.readTree(rotated.body());
        assertThat(next.path("refreshToken").asText()).isNotEqualTo(initial.path("refreshToken").asText());
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", initial.path("refreshToken").asText()), null).statusCode()).isEqualTo(401);
        assertThat(post("/api/auth/logout", Map.of("refreshToken", next.path("refreshToken").asText()), next.path("accessToken").asText()).statusCode()).isEqualTo(204);
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", next.path("refreshToken").asText()), null).statusCode()).isEqualTo(401);
    }

    @Test void invalidRefreshPayloadIsBadRequestInsteadOfServerError() throws Exception {
        assertThat(post("/api/auth/refresh", Map.of(), null).statusCode()).isEqualTo(400);
        assertThat(post("/api/auth/refresh", Map.of("refreshToken", "  "), null).statusCode()).isEqualTo(400);
    }

    @Test void passwordByteLengthOverBcryptLimitReturnsBadRequest() throws Exception {
        String username = username();
        String password = "需求".repeat(25);
        assertThat(password.length()).isLessThanOrEqualTo(72);
        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isGreaterThan(72);
        assertThat(post("/api/auth/register", Map.of("username", username, "password", password), null).statusCode()).isEqualTo(400);
    }

    @Test void loginRejectsWrongPasswordAndDuplicateRegistrationIsConflict() throws Exception {
        String username = username();
        var credentials = Map.of("username", username, "password", "study-password");
        assertThat(post("/api/auth/register", credentials, null).statusCode()).isEqualTo(201);
        assertThat(post("/api/auth/register", credentials, null).statusCode()).isEqualTo(409);
        assertThat(post("/api/auth/login", Map.of("username", username, "password", "incorrect-password"), null).statusCode()).isEqualTo(401);
        assertThat(post("/api/auth/login", credentials, null).statusCode()).isEqualTo(200);
    }

    @Test void dependencyFailureKeepsSessionAndReturnsServiceUnavailable() throws Exception {
        var session = register();
        String token = session.path("accessToken").asText();
        when(graph.retrieve(anyString(), anyInt())).thenThrow(new ResourceAccessException("connection refused"));
        assertThat(get("/api/knowledge/graph?entity=UML", token).statusCode()).isEqualTo(503);
        assertThat(get("/api/auth/me", token).statusCode()).isEqualTo(200);
        assertThat(get("/api/auth/me", null).statusCode()).isEqualTo(401);
    }

    @Test void malformedNumericQueryParameterReturnsBadRequest() throws Exception {
        var session = register();
        assertThat(get("/api/quizzes?knowledgePointId=abc", session.path("accessToken").asText()).statusCode()).isEqualTo(400);
    }

    JsonNode register() throws Exception {
        var response = post("/api/auth/register", Map.of("username", username(), "password", "study-password"), null);
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body());
    }
    String username() { return "test_" + UUID.randomUUID().toString().replace("-", ""); }
    HttpResponse<String> post(String uri, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + uri)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String uri, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + uri));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
