package edu.wfust.learning.platform.course.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/courses/{courseId}/study")
@PreAuthorize("isAuthenticated()")
public class CourseStudyController {
    private final CourseStudyService service;
    public CourseStudyController(CourseStudyService service) { this.service = service; }
    @GetMapping
    public CourseStudyService.StudyView study(@PathVariable Long courseId, Authentication authentication) {
        return service.study(courseId, owner(authentication));
    }
    @PutMapping("/sections/{sectionId}/progress")
    public CourseStudyService.ProgressView progress(@PathVariable Long courseId, @PathVariable Long sectionId,
        @Valid @RequestBody ProgressRequest request, Authentication authentication) {
        return service.saveProgress(courseId, sectionId, owner(authentication), request.completed());
    }
    private Long owner(Authentication authentication) {
        try {
            long id = Long.parseLong(authentication.getName());
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
    }
    public record ProgressRequest(@NotNull Boolean completed) { }
}
