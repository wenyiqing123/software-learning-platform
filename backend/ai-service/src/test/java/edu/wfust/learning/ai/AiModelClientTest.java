package edu.wfust.learning.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class AiModelClientTest {
    @Test void compatibleProviderReceivesCorrectPathAndConfiguredOptionsAndReturnsUsage() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200,
                    "{\"id\":\"completion-test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fixed-model\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"OK\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":4,\"total_tokens\":16}}", 0);
            ModelSettings settings = new ModelSettings(http.baseUrl() + "/proxy/v1/", "test-key", "fixed-model",
                    0.2, 128, Duration.ofSeconds(2));
            AiModelClient client = new AiModelClient(settings);
            var response = client.call("  {json: [1, 2]}\n  ");
            HttpStub.Request request = http.takeRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/proxy/v1/chat/completions");
            var body = JsonMapper.builder().build().readTree(request.body());
            assertThat(body.path("model").asText()).isEqualTo("fixed-model");
            assertThat(body.path("temperature").asDouble()).isEqualTo(0.2);
            assertThat(body.path("max_tokens").asInt()).isEqualTo(128);
            assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("  {json: [1, 2]}\n  ");
            assertThat(response.getResult().getOutput().getText()).isEqualTo("OK");
            assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(16);
        }
    }

    @Test void sendsOptionalSystemMessageBeforeTheUnmodifiedUserPrompt() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200,
                    "{\"id\":\"completion-test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fixed-model\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}", 0);
            AiModelClient client = new AiModelClient(new ModelSettings(http.baseUrl(), "test-key", "fixed-model",
                    0.2, 128, Duration.ofSeconds(2)));

            client.call("  original {prompt}\n", "concise system instruction");

            var messages = JsonMapper.builder().build().readTree(http.takeRequest().body()).path("messages");
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
            assertThat(messages.get(0).path("content").asText()).isEqualTo("concise system instruction");
            assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
            assertThat(messages.get(1).path("content").asText()).isEqualTo("  original {prompt}\n");
        }
    }

    @Test void dedicatedGenerationCanOverrideTokenBudgetForReasoningModels() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200,
                    "{\"id\":\"completion-test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fixed-model\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"正文\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":4,\"total_tokens\":16}}", 0);
            AiModelClient client = new AiModelClient(new ModelSettings(http.baseUrl(), "test-key", "fixed-model",
                    0.2, 1024, Duration.ofSeconds(2)));

            var response = client.call("微课上下文", "直接输出学习者可读正文，不要只输出思考过程。", 2048);

            var body = JsonMapper.builder().build().readTree(http.takeRequest().body());
            assertThat(body.path("max_tokens").asInt()).isEqualTo(2048);
            assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
            assertThat(response.getResult().getOutput().getText()).isEqualTo("正文");
        }
    }

    @Test void providerFailureIsSanitizedAndNotRetried() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(503, "{\"error\":{\"message\":\"Bearer sk-sensitive\"}}", 0);
            AiModelClient client = new AiModelClient(new ModelSettings(http.baseUrl(), "test-key", "fixed-model",
                    0.2, 128, Duration.ofSeconds(1)));
            assertThatThrownBy(() -> client.call("question")).isInstanceOf(AiFailure.class)
                    .hasMessageNotContaining("sk-sensitive");
            http.takeRequest();
            assertThat(http.requests).isEmpty();
        }
    }

    @Test void successfulProviderResponseWithEmptyContentIsReportedAsModelEmpty() throws Exception {
        try (HttpStub http = new HttpStub()) {
            String body = """
                    {"id":"empty","object":"chat.completion","created":1,"model":"fixed-model","choices":[{"index":0,"message":{"role":"assistant","content":"   "},"finish_reason":"stop"}]}
                    """;
            http.responder = request -> new HttpStub.Reply(200, body, 0);
            AiModelClient client = new AiModelClient(new ModelSettings(http.baseUrl(), "test-key", "fixed-model",
                    0.2, 128, Duration.ofSeconds(1)));
            assertThatThrownBy(() -> client.call("question"))
                    .isInstanceOfSatisfying(AiFailure.class, failure -> assertThat(failure.code()).isEqualTo("MODEL_EMPTY"));
        }
    }

    @Test void modelHttpTimeoutIsBoundedAndSafe() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200, "{}", 1500);
            AiModelClient client = new AiModelClient(new ModelSettings(http.baseUrl(), "test-key", "fixed-model",
                    0.2, 128, Duration.ofMillis(100)));
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatThrownBy(() -> client.call("question")).isInstanceOf(AiFailure.class));
            http.takeRequest();
            assertThat(http.requests).isEmpty();
        }
    }
}
