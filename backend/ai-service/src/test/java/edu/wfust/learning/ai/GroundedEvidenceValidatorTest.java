package edu.wfust.learning.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundedEvidenceValidatorTest {
    private final GroundedEvidenceValidator validator = new GroundedEvidenceValidator(JsonMapper.builder().build());

    @Test
    void acceptsOnlyAnswersThatContainEveryEvidenceField() {
        var evidence = List.of(new GroundedRankedAnswer.Evidence("T1", "加工", "命名", "动宾短语", 0.9));

        assertThat(validator.coversFacts("加工命名动宾短语", evidence)).isTrue();
        assertThat(validator.coversFacts("加工应当命名为名词短语", evidence)).isFalse();
    }

    @Test
    void classifiesExplicitComparisonAndMultiQuestionPromptsAsMultiFact() {
        assertThat(validator.requiresMultipleEvidence("分别说明输入和输出？", "SINGLE_FACT")).isTrue();
        assertThat(validator.requiresMultipleEvidence("什么是数据流图？", "SINGLE_FACT")).isFalse();
    }
}
