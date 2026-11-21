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
public class MasteryMapController {
    private final MasteryMapService service;
    public MasteryMapController(MasteryMapService service) { this.service = service; }
    @GetMapping("/mastery-map")
    public MasteryMapService.MapView map(@RequestParam(required = false) @Positive Long courseId, Authentication authentication) {
        return service.map(courseId, Long.parseLong(authentication.getName()));
    }
}
