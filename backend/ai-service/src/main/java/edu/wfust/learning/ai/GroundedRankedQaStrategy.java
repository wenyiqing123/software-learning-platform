package edu.wfust.learning.ai;

/** Handles the evidence-selection and evidence-constrained RANKED path. */
final class GroundedRankedQaStrategy implements QaExecutionStrategy {
    @Override public Family family() { return Family.GROUNDED_RANKED; }

    @Override public boolean supports(QaMode mode) { return mode == QaMode.RANKED; }

    @Override public Outcome execute(Context context) {
        return Outcome.from(context.groundedRanked().execute(context.question(), context.knowledge(), context.prompt()));
    }
}
