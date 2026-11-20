package edu.wfust.learning.platform.experiment;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class ExperimentRequestTest {
    @Test void omittedAblationFlagDefaultsToFalse() {
        var request = new ObjectMapper().readValue("{\"question\":\"需求分析是什么\"}", AiExperimentController.RunRequest.class);
        assertThat(request.includeAblations()).isFalse();
    }

    @Test void omittedExperimentTypeKeepsTheExistingMethodComparison() {
        var request = new ObjectMapper().readValue("{\"question\":\"软件工程是什么\"}", AiExperimentController.RunRequest.class);
        assertThat(request.experimentType()).isEqualTo("METHODS");
    }

    @Test void qwenMatrixIsAnExplicitSeparateExperimentType() {
        var request = new ObjectMapper().readValue("{\"question\":\"软件工程是什么\",\"experimentType\":\"QWEN_MATRIX\"}", AiExperimentController.RunRequest.class);
        assertThat(request.experimentType()).isEqualTo("QWEN_MATRIX");
    }

    @Test void methodComparisonIsTheDefaultExperimentType() {
        var request = new ObjectMapper().readValue("{\"question\":\"软件工程是什么\"}", AiExperimentController.RunRequest.class);
        assertThat(request.experimentType()).isEqualTo("METHODS");
    }
}
