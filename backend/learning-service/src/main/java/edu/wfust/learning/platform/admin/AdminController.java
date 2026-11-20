package edu.wfust.learning.platform.admin;

import edu.wfust.learning.platform.entity.CourseEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService service;
    private final PrerequisiteQualityService prerequisiteQualityService;
    public AdminController(AdminService service, PrerequisiteQualityService prerequisiteQualityService) {
        this.service = service;
        this.prerequisiteQualityService = prerequisiteQualityService;
    }
    @GetMapping("/courses") public List<CourseEntity> courses() { return service.courses(); }
    @PostMapping("/courses") @ResponseStatus(HttpStatus.CREATED)
    public CourseEntity createCourse(@Valid @RequestBody AdminPayloads.CourseRequest request) { return service.createCourse(request); }
    @PutMapping("/courses/{id}")
    public CourseEntity updateCourse(@PathVariable Long id, @Valid @RequestBody AdminPayloads.CourseRequest request) { return service.updateCourse(id, request); }
    @PutMapping("/courses/{id}/enabled")
    public CourseEntity setCourseEnabled(@PathVariable Long id, @Valid @RequestBody AdminPayloads.EnabledRequest request) { return service.setCourseEnabled(id, request.enabled()); }
    @GetMapping("/knowledge-points")
    public List<AdminService.PointView> points(@RequestParam Long courseId) { return service.knowledgePoints(courseId); }
    @GetMapping("/courses/{courseId}/prerequisite-quality")
    public PrerequisiteQualityService.QualityReport prerequisiteQuality(@PathVariable Long courseId) {
        return prerequisiteQualityService.quality(courseId);
    }
    @GetMapping("/questions") public List<AdminService.QuestionView> questions(@RequestParam(required = false) Long courseId) { return service.questions(courseId); }
    @PostMapping("/questions") @ResponseStatus(HttpStatus.CREATED)
    public AdminService.QuestionView createQuestion(@Valid @RequestBody AdminPayloads.QuestionRequest request) { return service.createQuestion(request); }
    @PutMapping("/questions/{id}")
    public AdminService.QuestionView updateQuestion(@PathVariable Long id, @Valid @RequestBody AdminPayloads.QuestionRequest request) { return service.updateQuestion(id, request); }
    @PutMapping("/questions/{id}/enabled")
    public AdminService.QuestionView setQuestionEnabled(@PathVariable Long id, @Valid @RequestBody AdminPayloads.EnabledRequest request) { return service.setQuestionEnabled(id, request.enabled()); }
}
