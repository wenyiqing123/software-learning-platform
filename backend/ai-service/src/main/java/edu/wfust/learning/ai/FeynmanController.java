package edu.wfust.learning.ai;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/feynman")
@Validated
@PreAuthorize("hasRole('STUDENT')")
public class FeynmanController {
    private final FeynmanEvaluationService service;

    public FeynmanController(FeynmanEvaluationService service) {
        this.service = service;
    }

    @PostMapping("/evaluate")
    public FeynmanEvaluateResponse evaluate(@Valid @RequestBody FeynmanEvaluateRequest request) {
        return service.evaluate(request);
    }
}
