package edu.wfust.learning.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnswerRequest(@NotBlank @Size(max = 2000) String question, QaMode mode,
                            @Size(max = 10) List<@NotBlank @Size(max = 2000) String> historyMessages,
                            @Size(max = 20) List<@NotBlank @Size(max = 200) String> historyEntities) {
    public AnswerRequest {
        if (mode == null) mode = QaMode.RANKED;
        historyMessages = historyMessages == null ? List.of() : List.copyOf(historyMessages);
        historyEntities = historyEntities == null ? List.of() : List.copyOf(historyEntities);
    }
    public AnswerRequest(String question, QaMode mode) { this(question, mode, List.of(), List.of()); }
}
