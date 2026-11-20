package edu.wfust.learning.platform.quiz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuizGraderTest {
    private final QuizGrader grader = new QuizGrader();

    @Test
    void comparesMultiSelectAnswersWithoutOrderSensitivity() {
        assertThat(grader.isCorrect(List.of("B", "A"), List.of("A", "B"))).isTrue();
    }

    @Test
    void rejectsMissingExtraDuplicateAndEmptySubmissions() {
        assertThat(grader.isCorrect(List.of("A"), List.of("A", "B"))).isFalse();
        assertThat(grader.isCorrect(List.of("A", "B", "C"), List.of("A", "B"))).isFalse();
        assertThat(grader.isCorrect(List.of("A", "A", "B"), List.of("A", "B"))).isFalse();
        assertThat(grader.isCorrect(List.of(), List.of("A"))).isFalse();
    }
}
