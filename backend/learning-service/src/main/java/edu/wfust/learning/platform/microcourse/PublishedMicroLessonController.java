package edu.wfust.learning.platform.microcourse;

import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/study/sections/{sectionId}/micro-lesson")
public class PublishedMicroLessonController {
    private final MicroLessonService service;
    public PublishedMicroLessonController(MicroLessonService service) { this.service = service; }
    @GetMapping
    public MicroLessonService.DraftView published(@PathVariable @Positive Long courseId, @PathVariable @Positive Long sectionId) {
        return service.published(courseId, sectionId);
    }
}
