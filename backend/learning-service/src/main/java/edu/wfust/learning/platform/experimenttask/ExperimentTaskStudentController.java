package edu.wfust.learning.platform.experimenttask;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/experiment-tasks")
public class ExperimentTaskStudentController {
    private final ExperimentTaskService service;
    public ExperimentTaskStudentController(ExperimentTaskService service){this.service=service;}
    @GetMapping public List<ExperimentTaskService.AssignmentView> assignments(Authentication auth){return service.assignments(Long.parseLong(auth.getName()));}
    @PostMapping("/{id}/start") public ExperimentTaskService.AssignmentView start(Authentication auth,@PathVariable Long id){return service.start(Long.parseLong(auth.getName()),id);}
}
