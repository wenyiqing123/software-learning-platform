package edu.wfust.learning.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QaStrategySelectorTest {
    private final QaStrategySelector selector = QaStrategySelector.defaultSelector();

    @Test
    void routesInteractiveAndExperimentModesToStableStrategyFamilies() {
        assertThat(selector.select(QaMode.DIRECT).family()).isEqualTo(QaExecutionStrategy.Family.CHAT);
        assertThat(selector.select(QaMode.TRIPLES).family()).isEqualTo(QaExecutionStrategy.Family.CHAT);
        assertThat(selector.select(QaMode.RANKED).family()).isEqualTo(QaExecutionStrategy.Family.GROUNDED_RANKED);
        assertThat(selector.select(QaMode.ABLATION_SORTED_SCORED).family()).isEqualTo(QaExecutionStrategy.Family.CHAT);
        assertThat(selector.select(QaMode.ABLATION_WITHOUT_SCORE).family()).isEqualTo(QaExecutionStrategy.Family.CHAT);
        assertThat(selector.select(QaMode.ABLATION_WITHOUT_SORT).family()).isEqualTo(QaExecutionStrategy.Family.CHAT);
        assertThat(selector.select(QaMode.ABLATION_WITHOUT_SORT_AND_SCORE).family()).isEqualTo(QaExecutionStrategy.Family.CHAT);
    }

    @Test
    void rejectsNullModeInsteadOfFallingBackToAValidAnswerStrategy() {
        assertThatThrownBy(() -> selector.select(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode");
    }
}
