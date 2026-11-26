package edu.wfust.learning.platform.experimenttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/experiment-tasks")
@PreAuthorize("hasRole('ADMIN')")
public class ExperimentTaskController {
    private final ExperimentTaskService service;
    public ExperimentTaskController(ExperimentTaskService service){this.service=service;}
    @GetMapping public List<ExperimentTaskService.TaskView> list(){return service.listTasks();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ExperimentTaskService.TaskView create(Authentication auth,@Valid @RequestBody ExperimentTaskService.CreateTaskRequest request){return service.createTask(Long.parseLong(auth.getName()),request);}
    @GetMapping("/{id}/groups") public List<ExperimentTaskService.GroupView> groups(@PathVariable Long id){return service.listGroups(id);}
    @GetMapping("/{id}/assignments") public List<ExperimentTaskService.AdminAssignmentView> assignments(@PathVariable Long id){return service.listAssignments(id);}
    @PostMapping("/{id}/groups") @ResponseStatus(HttpStatus.CREATED) public ExperimentTaskService.GroupView group(@PathVariable Long id,@Valid @RequestBody ExperimentTaskService.GroupRequest request){return service.addGroup(id,request);}
    @PostMapping("/{id}/assign") @ResponseStatus(HttpStatus.NO_CONTENT) public void assign(@PathVariable Long id,@Valid @RequestBody AssignRequest request){service.assign(id,request.groupId(),request.userIds());}
    @PutMapping("/{id}/status") public ExperimentTaskService.TaskView status(@PathVariable Long id,@Valid @RequestBody StatusRequest request){return service.setStatus(id,request.status());}
    @GetMapping("/export") public ResponseEntity<byte[]> export(@RequestParam(required=false) Long taskId){var headers=new HttpHeaders();headers.setContentType(new MediaType("text","csv",StandardCharsets.UTF_8));headers.set(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=experiment-tasks.csv");return ResponseEntity.ok().headers(headers).body(service.exportCsv(taskId).getBytes(StandardCharsets.UTF_8));}
    public record AssignRequest(@NotNull Long groupId,@NotEmpty List<Long> userIds){}
    public record StatusRequest(@NotNull String status){}
}
