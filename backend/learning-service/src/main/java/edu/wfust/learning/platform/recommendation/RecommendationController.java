package edu.wfust.learning.platform.recommendation;

import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@PreAuthorize("hasRole('STUDENT')")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }
    @GetMapping public List<RecommendationService.Recommendation> list(Authentication authentication) {
        return service.forUser(Long.parseLong(authentication.getName()));
    }
}
