package edu.wfust.learning.platform.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
public class QuizDiagnosisController {
    private final QuizDiagnosisService service;
    public QuizDiagnosisController(QuizDiagnosisService service) { this.service = service; }

    @PostMapping("/{questionId}/diagnosis")
    public List<QuizDiagnosisService.DiagnosisView> diagnose(@PathVariable Long questionId,
            @Valid @RequestBody DiagnosisRequest request) {
        return service.diagnose(questionId, request.selected());
    }

    public record DiagnosisRequest(@NotEmpty @Size(max = 8)
        List<@NotBlank @Pattern(regexp = "[A-H]|TRUE|FALSE") String> selected) { }
}
