package edu.wfust.learning.platform.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class AdminPayloads {
    private AdminPayloads() { }
    public record CourseRequest(@NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 40) String term,
        @NotBlank @Size(max = 500) String description, @NotNull @Positive @Max(1) Integer graphSpaceId) { }
    public record Option(@NotBlank @Pattern(regexp = "[A-H]|TRUE|FALSE") String key,
        @NotBlank @Size(max = 2000) String text) { }
    public record QuestionRequest(@NotNull @Positive Long courseId, @NotNull @Positive Long knowledgePointId,
        @NotBlank String type, @NotBlank @Size(max = 10000) String stem,
        @NotNull @Size(min = 2, max = 8) List<@NotNull @Valid Option> options,
        @NotNull @Size(min = 1, max = 8) List<@NotBlank String> answer,
        @NotBlank @Size(max = 10000) String explanation, @NotNull @Min(1) @Max(5) Integer difficulty) { }
    public record EnabledRequest(@NotNull Boolean enabled) { }
    public record KnowledgePointRequest(
        @NotNull @Positive Long courseId,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 200) String graphEntity,
        @Size(max = 800) String description,
        List<@NotNull @Positive Long> prerequisiteIds,
        @Min(0) @Max(9999) Integer sortOrder
    ) { }
}
