package edu.wfust.learning.ai;

import java.time.Duration;

/** Optional private model configuration for interactive RANKED answers. */
public final class RankedModelSettings {
    private final boolean enabled;
    private final boolean matrixEnabled;
    private final ModelSettings modelSettings;
    private final ModelSettings baseModelSettings;

    public RankedModelSettings(
            boolean enabled, String baseUrl, String apiKey, String model, String baseModel,
            double temperature, int maxTokens, Duration httpTimeout, boolean matrixEnabled) {
        this(enabled, baseUrl, apiKey, model, baseModel, temperature, maxTokens, httpTimeout, matrixEnabled, true);
    }

    private RankedModelSettings(boolean enabled, String baseUrl, String apiKey, String model, String baseModel,
                                double temperature, int maxTokens, Duration httpTimeout, boolean matrixEnabled,
                                boolean matrixConfiguredExplicitly) {
        this.enabled = enabled;
        this.modelSettings = new ModelSettings(baseUrl, apiKey, model, temperature, maxTokens, httpTimeout);
        this.matrixEnabled = matrixEnabled;
        if (matrixEnabled && (baseModel == null || baseModel.isBlank() || model.equals(baseModel))) {
            throw new IllegalArgumentException("Ranked base and adapter model identifiers must differ");
        }
        this.baseModelSettings = matrixEnabled
                ? new ModelSettings(baseUrl, apiKey, baseModel, temperature, maxTokens, httpTimeout) : null;
    }

    static RankedModelSettings forMatrix(boolean enabled, String baseUrl, String apiKey, String model, String baseModel,
                                         double temperature, int maxTokens, Duration httpTimeout, boolean matrixEnabled) {
        return new RankedModelSettings(enabled, baseUrl, apiKey, model, baseModel,
                temperature, maxTokens, httpTimeout, matrixEnabled, true);
    }

    static RankedModelSettings forRanked(boolean enabled, String baseUrl, String apiKey, String model,
                                         double temperature, int maxTokens, Duration httpTimeout) {
        return new RankedModelSettings(enabled, baseUrl, apiKey, model, "qwen35-base", temperature,
                maxTokens, httpTimeout, false, true);
    }

    public boolean enabled() { return enabled; }
    public boolean configured() { return enabled && modelSettings.configured(); }
    public boolean matrixEnabled() { return matrixEnabled; }
    public boolean matrixConfigured() {
        return matrixEnabled && modelSettings.configured()
                && baseModelSettings != null && baseModelSettings.configured();
    }
    public String baseModel() { return baseModelSettings == null ? "qwen35-base" : baseModelSettings.model(); }
    public String configurationError() { return modelSettings.configurationError(); }
    public ModelSettings modelSettings() { return modelSettings; }
    public ModelSettings baseModelSettings() { return baseModelSettings; }
    public void requireMatrixConfigured() {
        if (!matrixEnabled) throw new AiFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "QWEN_MATRIX_DISABLED", "Qwen 基座与微调模型对照尚未启用。", false);
        if (!modelSettings.configured()) modelSettings.requireConfigured();
        if (baseModelSettings == null) throw AiFailure.model(null);
        baseModelSettings.requireConfigured();
    }
    public void requireConfigured() {
        if (!enabled) throw new AiFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "RANKED_MODEL_DISABLED", "排序问答微调模型未启用。", false);
        modelSettings.requireConfigured();
    }
}
