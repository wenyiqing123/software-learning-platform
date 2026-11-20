package edu.wfust.learning.platform.quiz;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpacedReviewPolicyTest {
    private final SpacedReviewPolicy policy = new SpacedReviewPolicy();

    @Test void schedulesTransparentIntervalsForCorrectAnswers() {
        assertThat(policy.nextIntervalMinutes(0, true)).isEqualTo(10);
        assertThat(policy.nextIntervalMinutes(1, true)).isEqualTo(1_440);
        assertThat(policy.nextIntervalMinutes(2, true)).isEqualTo(4_320);
        assertThat(policy.nextIntervalMinutes(3, true)).isEqualTo(10_080);
    }

    @Test void wrongAnswersCannotClaimGoodOrEasyRecall() {
        assertThat(policy.nextIntervalMinutes(0, false)).isEqualTo(10);
        assertThat(policy.nextIntervalMinutes(1, false)).isEqualTo(10);
        assertThat(policy.nextIntervalMinutes(2, false)).isEqualTo(10);
        assertThat(policy.nextIntervalMinutes(3, false)).isEqualTo(10);
    }

    @Test void rejectsGradesOutsideTheDefinedScale() {
        assertThatThrownBy(() -> policy.nextIntervalMinutes(-1, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.nextIntervalMinutes(4, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
