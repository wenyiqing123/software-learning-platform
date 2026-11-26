package edu.wfust.learning.platform.flashcard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/flashcards")
@PreAuthorize("hasRole('STUDENT')")
public class FlashcardController {
    private final FlashcardService service;

    public FlashcardController(FlashcardService service) {
        this.service = service;
    }

    @GetMapping
    public List<FlashcardService.FlashcardView> list(
        Authentication auth,
        @RequestParam(required = false) Long courseId,
        @RequestParam(defaultValue = "false") boolean dueOnly
    ) {
        return service.list(owner(auth), courseId, dueOnly);
    }

    @PostMapping("/{knowledgePointId}/review")
    public FlashcardService.FlashcardView review(
        Authentication auth,
        @PathVariable Long knowledgePointId,
        @Valid @RequestBody ReviewRequest request
    ) {
        return service.review(owner(auth), knowledgePointId, request.recallRating());
    }

    private long owner(Authentication auth) {
        return Long.parseLong(auth.getName());
    }

    public record ReviewRequest(@Min(0) @Max(3) int recallRating) { }
}
