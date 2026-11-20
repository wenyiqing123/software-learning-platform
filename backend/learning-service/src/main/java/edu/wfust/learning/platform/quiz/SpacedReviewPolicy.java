package edu.wfust.learning.platform.quiz;

/** Transparent fixed review intervals; this is intentionally not an FSRS implementation. */
public final class SpacedReviewPolicy {
    private static final int[] INTERVAL_MINUTES = {10, 1_440, 4_320, 10_080};

    public int nextIntervalMinutes(int recallRating, boolean objectivelyCorrect) {
        if (recallRating < 0 || recallRating >= INTERVAL_MINUTES.length) {
            throw new IllegalArgumentException("回忆评分必须在 0 到 3 之间");
        }
        if (!objectivelyCorrect) return INTERVAL_MINUTES[0];
        return INTERVAL_MINUTES[recallRating];
    }
}
