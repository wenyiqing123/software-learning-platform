package edu.wfust.learning.platform.experimenttask;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/experiment-tasks") @PreAuthorize("hasRole('STUDENT')")
public class ExperimentTaskStudentController {
    private final ExperimentTaskService service;
    public ExperimentTaskStudentController(ExperimentTaskService service){this.service=service;}
    @GetMapping public List<ExperimentTaskService.StudentAssignmentView> assignments(Authentication auth){return service.studentAssignments(Long.parseLong(auth.getName()));}
    @PostMapping("/{id}/start") public ExperimentTaskService.StudentAssignmentView start(Authentication auth,@PathVariable Long id){return service.startForStudent(Long.parseLong(auth.getName()),id);}
}
