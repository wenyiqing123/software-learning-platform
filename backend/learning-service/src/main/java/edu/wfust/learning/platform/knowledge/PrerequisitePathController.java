package edu.wfust.learning.platform.knowledge;

import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@Validated
@PreAuthorize("hasRole('STUDENT')")
public class PrerequisitePathController {
    private final PrerequisitePathService paths;

    public PrerequisitePathController(PrerequisitePathService paths) {
        this.paths = paths;
    }

    @GetMapping("/learning-path")
    public PrerequisitePathService.LearningPath path(@RequestParam @Positive Long targetId,
                                                     Authentication authentication) {
        return paths.path(targetId, Long.parseLong(authentication.getName()));
    }
}
