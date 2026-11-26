package edu.wfust.learning.platform.microcourse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/micro-lessons")
public class MicroLessonController {
    private final MicroLessonService service;
    public MicroLessonController(MicroLessonService service) { this.service = service; }

    @GetMapping
    public List<MicroLessonService.DraftView> list(@RequestParam(required = false) @Positive Long courseId) { return service.list(courseId); }

    @GetMapping("/courses/{courseId}/sections")
    public List<MicroLessonService.SectionOption> sections(@PathVariable Long courseId) { return service.sections(courseId); }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public MicroLessonService.DraftView generate(Authentication authentication, @RequestHeader("Authorization") String authorization,
                                                  @Valid @RequestBody MicroLessonService.GenerateRequest request) {
        return service.generate(Long.parseLong(authentication.getName()), authorization, request);
    }

    @PutMapping("/{id}")
    public MicroLessonService.DraftView updateDraft(@PathVariable Long id, @Valid @RequestBody MicroLessonService.UpdateDraftRequest request) {
        return service.updateDraft(id, request);
    }

    @PutMapping("/{id}/publish")
    public MicroLessonService.DraftView publish(@PathVariable Long id) { return service.publish(id); }
}
