package edu.wfust.learning.ai;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/tutor")
public class TutorHintController {
    private final TutorHintService service;
    public TutorHintController(TutorHintService service) { this.service = service; }

    @PostMapping("/hint")
    public TutorHintResponse hint(@Valid @RequestBody TutorHintRequest request) { return service.hint(request); }
}
