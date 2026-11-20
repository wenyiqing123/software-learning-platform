package edu.wfust.learning.ai;

/** Handles DIRECT, TRIPLES, and factorial ablation prompts with the configured chat model. */
final class ChatQaStrategy implements QaExecutionStrategy {
    @Override public Family family() { return Family.CHAT; }

    @Override public boolean supports(QaMode mode) { return mode != null; }

    @Override public Outcome execute(Context context) {
        return Outcome.from(context.model().call(context.prompt()));
    }
}
