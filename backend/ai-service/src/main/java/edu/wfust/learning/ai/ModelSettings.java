package edu.wfust.learning.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
public final class ModelSettings {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final Duration httpTimeout;
    private final String configurationError;

    public ModelSettings(@Value("${spring.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                         @Value("${spring.ai.openai.api-key:}") String apiKey,
                         @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
                         @Value("${spring.ai.openai.chat.options.temperature:0.2}") double temperature,
                         @Value("${spring.ai.openai.chat.options.max-tokens:1024}") int maxTokens,
                         @Value("${ai.model.http-timeout:30s}") Duration httpTimeout) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model == null ? "" : model.strip();
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.httpTimeout = httpTimeout;
        String normalized = "";
        String error = null;
        if (this.apiKey.isBlank() || Set.of("local-development-key", "your-api-key", "change-me", "sk-your-api-key")
                .contains(this.apiKey.toLowerCase(Locale.ROOT))) {
            error = "未配置有效的 OPENAI_API_KEY，模型服务尚未配置。";
        }
        try {
            URI uri = URI.create(baseUrl == null ? "" : baseUrl.strip());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPath().endsWith("/chat/completions")) {
                throw new IllegalArgumentException();
            }
            normalized = uri.toASCIIString().replaceAll("/+$", "");
            if (uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/")) normalized += "/v1";
        } catch (IllegalArgumentException exception) {
            if (error == null) error = "OPENAI_BASE_URL 必须是有效的 HTTP(S) 模型基础地址，不能包含凭据、查询参数或 chat/completions。";
        }
        if (error == null && (this.model.isBlank() || !Double.isFinite(temperature) || temperature < 0 || temperature > 2
                || maxTokens < 1 || maxTokens > 131072 || httpTimeout == null || httpTimeout.isNegative()
                || httpTimeout.isZero() || httpTimeout.compareTo(Duration.ofSeconds(120)) > 0)) {
            error = "模型名称、temperature、token 上限或 HTTP 超时配置无效。";
        }
        this.baseUrl = normalized;
        this.configurationError = error;
    }
    public boolean configured() { return configurationError == null; }
    public String configurationError() { return configurationError; }
    public String baseUrl() { return baseUrl; }
    public String model() { return model; }
    public double temperature() { return temperature; }
    public int maxTokens() { return maxTokens; }
    public Duration httpTimeout() { return httpTimeout; }
    public void requireConfigured() {
        if (!configured()) throw new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_NOT_CONFIGURED", configurationError, false);
    }
    public OpenAiChatOptions options() {
        requireConfigured();
        return OpenAiChatOptions.builder().baseUrl(baseUrl).apiKey(apiKey).model(model).temperature(temperature)
                .maxTokens(maxTokens).timeout(httpTimeout).maxRetries(0).streamUsage(true).build();
    }
}
