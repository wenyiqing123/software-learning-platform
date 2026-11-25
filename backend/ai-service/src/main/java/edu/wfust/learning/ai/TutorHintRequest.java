package edu.wfust.learning.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TutorHintRequest(
        @NotBlank @Size(max = 2000) String question,
        @Min(1) @Max(3) int level,
        @Size(max = 10) List<@NotBlank @Size(max = 2000) String> historyMessages,
        @Size(max = 20) List<@NotBlank @Size(max = 200) String> historyEntities,
        @Size(max = 10) List<@NotBlank @Size(max = 200) String> entities) {
    public TutorHintRequest {
        historyMessages = historyMessages == null ? List.of() : List.copyOf(historyMessages);
        historyEntities = historyEntities == null ? List.of() : List.copyOf(historyEntities);
        entities = entities == null ? List.of() : List.copyOf(entities);
    }
    public TutorHintRequest(String question, int level) { this(question, level, List.of(), List.of(), List.of()); }
    public TutorHintRequest(String question, int level, List<String> historyMessages, List<String> historyEntities) {
        this(question, level, historyMessages, historyEntities, List.of());
    }
}
