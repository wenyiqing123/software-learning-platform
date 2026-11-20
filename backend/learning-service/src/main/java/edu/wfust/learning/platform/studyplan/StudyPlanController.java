package edu.wfust.learning.platform.studyplan;


import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/study-plans")
public class StudyPlanController {
    private final StudyPlanService service;
    public StudyPlanController(StudyPlanService service){this.service=service;}
    @GetMapping("/today") public StudyPlanService.PlanView today(Authentication auth){return service.today(owner(auth));}
    @PostMapping public StudyPlanService.PlanView generate(Authentication auth,@Valid @RequestBody GenerateRequest request){return service.generate(owner(auth),request.budgetMinutes());}
    @PutMapping("/tasks/{taskId}") public StudyPlanService.PlanTaskView update(Authentication auth,@PathVariable Long taskId,@Valid @RequestBody TaskAction request){
        return service.updateTask(owner(auth),taskId,request.action());
    }
    private long owner(Authentication auth){return Long.parseLong(auth.getName());}
    public record GenerateRequest(@Min(5) @Max(240) int budgetMinutes) { }
    public record TaskAction(@jakarta.validation.constraints.Pattern(regexp = "COMPLETE|SKIP") String action) { }
}
