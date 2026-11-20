package edu.wfust.learning.platform.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/questions/{questionId}/diagnostics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiagnosisController {
    private final QuizDiagnosisService service;
    public AdminDiagnosisController(QuizDiagnosisService service) { this.service = service; }

    @GetMapping
    public List<QuizDiagnosisService.ConfiguredView> list(@PathVariable Long questionId) { return service.configured(questionId); }

    @PutMapping
    public List<QuizDiagnosisService.ConfiguredView> replace(@PathVariable Long questionId,
            @Valid @RequestBody ConfigurationRequest request) {
        return service.replace(questionId, request.entries().stream()
            .map(entry -> new QuizDiagnosisService.ConfigEntry(entry.optionKey(), entry.knowledgePointId(), entry.rationale())).toList());
    }

    public record ConfigurationRequest(@NotNull @Size(max = 8) List<@NotNull @Valid Entry> entries) { }
    public record Entry(@NotBlank @Pattern(regexp = "[A-H]|TRUE|FALSE") String optionKey,
                        @NotNull @Positive Long knowledgePointId,
                        @NotBlank @Size(max = 500) String rationale) { }
}
