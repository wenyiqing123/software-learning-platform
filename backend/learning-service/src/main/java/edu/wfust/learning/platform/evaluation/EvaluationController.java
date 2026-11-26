package edu.wfust.learning.platform.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@PreAuthorize("hasRole('STUDENT')")
public class EvaluationController {
    private final EvaluationService service;
    public EvaluationController(EvaluationService service){this.service=service;}
    @GetMapping("/sessions") public List<EvaluationService.SessionView> mine(Authentication auth){return service.mine(owner(auth));}
    @PostMapping("/sessions") public EvaluationService.SessionView start(Authentication auth,@Valid @RequestBody StartBody body){return service.start(owner(auth),new EvaluationService.StartRequest(body.conditionKey(),body.taskKey(),body.taskOrder(),body.assignmentId(),body.consentVersion(),body.consentAccepted()));}
    @PostMapping("/sessions/{id}/measurements") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void measure(Authentication auth,@PathVariable Long id,@Valid @RequestBody MeasureBody body){service.measure(owner(auth),id,new EvaluationService.MeasureRequest(body.phase(),body.score(),body.responses()));}
    @PostMapping("/sessions/{id}/complete") public EvaluationService.SessionView complete(Authentication auth,@PathVariable Long id,@Valid @RequestBody CompleteBody body){return service.complete(owner(auth),id,new EvaluationService.CompleteRequest(body.elapsedSeconds(),body.psychologicalEffort(),body.feedback()));}
    @DeleteMapping("/sessions/{id}") @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void abandon(Authentication auth,@PathVariable Long id){service.abandon(owner(auth),id);}
    private long owner(Authentication auth){return Long.parseLong(auth.getName());}
    public record StartBody(@NotBlank @Size(max=60) String conditionKey,@NotBlank @Size(max=120) String taskKey,@Size(max=120) String taskOrder,Long assignmentId,@NotBlank @Size(max=30) String consentVersion,boolean consentAccepted) { }
    public record MeasureBody(@NotBlank String phase,@DecimalMin("0") @DecimalMax("100") double score,List<String> responses) { }
    public record CompleteBody(@Min(1) @Max(86400) int elapsedSeconds,@Min(1) @Max(9) int psychologicalEffort,@Size(max=2000) String feedback) { }
}
