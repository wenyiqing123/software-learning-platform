package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import org.springframework.ai.chat.model.ChatResponse;

/** A single generation family used by the compatibility facade. */
public interface QaExecutionStrategy {
    enum Family { CHAT, GROUNDED_RANKED }

    Family family();

    boolean supports(QaMode mode);

    Outcome execute(Context context);

    record Context(String question, PythonKnowledgeResponse knowledge, String prompt,
                   AiModelClient model, GroundedRankedAnswer groundedRanked) { }

    record Outcome(String answer, AnswerResponse.TokenUsage usage,
                   AiFailure failure, GroundedRankedAnswer.Trace grounding) {
        static Outcome from(ChatResponse response) {
            String answer = response == null || response.getResult() == null
                    || response.getResult().getOutput() == null
                    ? "" : response.getResult().getOutput().getText();
            return new Outcome(answer == null ? "" : answer, AnswerResponse.TokenUsage.from(response), null, null);
        }

        static Outcome from(GroundedRankedAnswer.Outcome outcome) {
            return new Outcome(outcome.answer(), outcome.usage(), outcome.failure(), outcome.trace());
        }
    }
}
