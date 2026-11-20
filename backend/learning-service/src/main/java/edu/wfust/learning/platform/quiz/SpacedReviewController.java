package edu.wfust.learning.platform.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/spaced-reviews")
public class SpacedReviewController {
    private final SpacedReviewService service;
    public SpacedReviewController(SpacedReviewService service) { this.service=service; }
    @GetMapping("/due")
    public List<SpacedReviewService.ReviewCard> due(Authentication auth,@RequestParam(required=false) Long wrongQuestionId) {
        return service.due(owner(auth),wrongQuestionId);
    }
    @GetMapping("/paused")
    public List<SpacedReviewService.ReviewCard> paused(Authentication auth) { return service.paused(owner(auth)); }
    @PostMapping("/{wrongQuestionId}/review")
    public SpacedReviewService.ReviewResult submit(Authentication auth,@PathVariable Long wrongQuestionId,
                                                    @Valid @RequestBody ReviewRequest request) {
        return service.submit(owner(auth),wrongQuestionId,new SpacedReviewService.ReviewRequest(request.answer(),request.recallRating(),request.requestKey()));
    }
    @PutMapping("/{wrongQuestionId}/state")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void state(Authentication auth,@PathVariable Long wrongQuestionId,@Valid @RequestBody StateRequest request) {
        service.setState(owner(auth),wrongQuestionId,request.state());
    }
    private long owner(Authentication auth) { return Long.parseLong(auth.getName()); }
    public record ReviewRequest(@NotEmpty List<String> answer,@Min(0) @Max(3) int recallRating,@NotNull UUID requestKey) { }
    public record StateRequest(@NotNull String state) { }
}
