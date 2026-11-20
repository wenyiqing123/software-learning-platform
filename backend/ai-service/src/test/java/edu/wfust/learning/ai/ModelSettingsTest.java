package edu.wfust.learning.ai;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.json.JsonMapper;

class ModelSettingsTest {
    private final ComparisonExecutor comparisons = new ComparisonExecutor(Duration.ofSeconds(150));
    @org.junit.jupiter.api.AfterEach void closeComparisons() { comparisons.close(); }
    private ModelSettings settings(String baseUrl, String key, String model, double temperature, int maxTokens) {
        return new ModelSettings(baseUrl, key, model, temperature, maxTokens, Duration.ofSeconds(2));
    }

    @Test void missingOrPlaceholderKeyIsUnconfiguredAndMakesNoModelOrPythonCall() {
        for (String key : new String[]{"", "  ", "local-development-key", "your-api-key"}) {
            ModelSettings settings = settings("https://api.openai.com", key, "fixed-model", 0.2, 128);
            ChatModel model = mock(ChatModel.class);
            PythonKnowledgeClient python = mock(PythonKnowledgeClient.class);
            var client = new AiModelClient(settings, model);
            var json = JsonMapper.builder().build();
            AnswerService service = new AnswerService(client, python,
                    new PromptStrategy(json), comparisons, new GroundedRankedAnswer(client, json));
            assertThat(settings.configured()).isFalse();
            assertThatThrownBy(() -> service.answer(new AnswerRequest("question", QaMode.RANKED)))
                    .isInstanceOf(AiFailure.class).hasMessageContaining("OPENAI_API_KEY");
            assertThatThrownBy(() -> service.compare("question"))
                    .isInstanceOf(AiFailure.class);
            verifyNoInteractions(model, python);
        }
    }

    @Test void compatibleProviderPathIsPreservedAndRootGetsV1ExactlyOnce() {
        assertThat(settings("https://api.openai.com/", "test-key", "model", 0.2, 128).baseUrl())
                .isEqualTo("https://api.openai.com/v1");
        assertThat(settings("https://provider.example/proxy/v1/", "test-key", "model", 0.2, 128).baseUrl())
                .isEqualTo("https://provider.example/proxy/v1");
    }

    @Test void invalidOptionsAndUnsafeUrisAreUnconfiguredWithSafeMessages() {
        for (ModelSettings settings : new ModelSettings[]{
                settings("file:///secret", "test-key", "model", 0.2, 128),
                settings("https://user:password@provider.example/v1", "test-key", "model", 0.2, 128),
                settings("https://provider.example/v1?key=secret", "test-key", "model", 0.2, 128),
                settings("https://provider.example/v1/chat/completions", "test-key", "model", 0.2, 128),
                settings("https://provider.example/v1", "test-key", "", 0.2, 128),
                settings("https://provider.example/v1", "test-key", "model", Double.NaN, 128),
                settings("https://provider.example/v1", "test-key", "model", 0.2, 0)}) {
            assertThat(settings.configured()).isFalse();
            assertThat(settings.configurationError()).doesNotContain("password", "key=secret", "test-key");
        }
    }
}
