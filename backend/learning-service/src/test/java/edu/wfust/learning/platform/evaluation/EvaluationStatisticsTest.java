package edu.wfust.learning.platform.evaluation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluationStatisticsTest {
    @Test
    void calculatesDescriptiveStatisticsAndPseudonymizesParticipantIds() {
        var rows = List.of(
                new EvaluationStatistics.Row(7L, "METHODS", "A", "1", 10, 4, 60.0, 70.0, 80.0),
                new EvaluationStatistics.Row(8L, "METHODS", "B", "2", 20, 6, 70.0, 80.0, 90.0),
                new EvaluationStatistics.Row(9L, "METHODS", "A", "3", 30, 8, 80.0, 90.0, 100.0),
                new EvaluationStatistics.Row(10L, "METHODS", "B", "4", 40, 2, 90.0, 100.0, 110.0));
        var summary = EvaluationStatistics.summarize("METHODS", rows);
        assertThat(summary.sampleCount()).isEqualTo(4);
        assertThat(summary.meanPreScore()).isEqualTo(75.0);
        assertThat(summary.medianPostScore()).isEqualTo(85.0);
        assertThat(summary.iqrDelayedScore()).isEqualTo(15.0);
        assertThat(EvaluationStatistics.participantCode(7L)).startsWith("P-").isNotEqualTo("P-7");
    }
}
