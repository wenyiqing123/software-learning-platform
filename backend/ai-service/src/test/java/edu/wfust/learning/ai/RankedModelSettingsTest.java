package edu.wfust.learning.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class RankedModelSettingsTest {
    @Test
    void comparisonMatrixIsOptInAndReusesTheRankedEndpointWithASeparateBaseModelId() {
        var disabled = RankedModelSettings.forRanked(true, "http://127.0.0.1:8998/v1", "qwen-secret",
                "qwen35-ranked-lora", 0, 512, Duration.ofSeconds(90));
        assertThat(disabled.enabled()).isTrue();
        assertThat(disabled.baseModelSettings()).isNull();

        var enabled = RankedModelSettings.forMatrix(true, "http://127.0.0.1:8998/v1", "qwen-secret",
                "qwen35-ranked-lora", "qwen35-base", 0, 512, Duration.ofSeconds(90), true);
        assertThat(enabled.matrixEnabled()).isTrue();
        assertThat(enabled.matrixConfigured()).isTrue();
        assertThat(enabled.baseModel()).isEqualTo("qwen35-base");
        assertThat(enabled.modelSettings().baseUrl()).isEqualTo(enabled.baseModelSettings().baseUrl());
        assertThat(enabled.modelSettings().model()).isEqualTo("qwen35-ranked-lora");
        assertThat(enabled.baseModelSettings().model()).isEqualTo("qwen35-base");
    }

    @Test
    void comparisonMatrixRejectsIdenticalModelIdentifiers() {
        assertThatThrownBy(() -> RankedModelSettings.forMatrix(true, "http://127.0.0.1:8998/v1", "qwen-secret",
                "same", "same", 0, 512, Duration.ofSeconds(90), true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("differ");
    }

    @Test
    void comparisonMatrixIsDisabledByDefault() {
        var settings = RankedModelSettings.forRanked(true, "http://127.0.0.1:8998/v1", "qwen-secret",
                "qwen35-ranked-lora", 0, 512, Duration.ofSeconds(90));
        assertThat(settings.matrixEnabled()).isFalse();
        assertThat(settings.matrixConfigured()).isFalse();
    }

    @Test
    void featureIsOffByDefaultEvenWhenNoDedicatedModelIsConfigured() {
        RankedModelSettings settings = RankedModelSettings.forRanked(false, "http://127.0.0.1:8998/v1",
                "", "qwen35-ranked-lora", 0, 512, Duration.ofSeconds(90));

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.configured()).isFalse();
        assertThat(settings.modelSettings().configured()).isFalse();
    }

    @Test
    void enabledConfigurationUsesThePrivateOpenAiCompatibleEndpoint() {
        RankedModelSettings settings = RankedModelSettings.forRanked(true, "http://127.0.0.1:8998/v1/",
                "qwen-secret", "qwen35-ranked-lora", 0, 512, Duration.ofSeconds(90));

        assertThat(settings.enabled()).isTrue();
        assertThat(settings.configured()).isTrue();
        assertThat(settings.modelSettings().baseUrl()).isEqualTo("http://127.0.0.1:8998/v1");
        assertThat(settings.modelSettings().model()).isEqualTo("qwen35-ranked-lora");
        assertThat(settings.modelSettings().temperature()).isZero();
        assertThat(settings.modelSettings().maxTokens()).isEqualTo(512);
    }

    @Test
    void enabledConfigurationRejectsPlaceholderKeyWithoutLeakingIt() {
        RankedModelSettings settings = RankedModelSettings.forRanked(true, "http://127.0.0.1:8998/v1",
                "change-me", "qwen35-ranked-lora", 0, 512, Duration.ofSeconds(90));

        assertThat(settings.configured()).isFalse();
        assertThat(settings.configurationError()).contains("OPENAI_API_KEY").doesNotContain("change-me");
    }
}
