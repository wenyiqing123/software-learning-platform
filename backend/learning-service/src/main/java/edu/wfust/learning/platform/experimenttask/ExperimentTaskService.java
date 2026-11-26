package edu.wfust.learning.platform.experimenttask;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.ExperimentAssignmentEntity;
import edu.wfust.learning.platform.entity.ExperimentGroupEntity;
import edu.wfust.learning.platform.entity.ExperimentTaskEntity;
import edu.wfust.learning.platform.mapper.ExperimentAssignmentMapper;
import edu.wfust.learning.platform.mapper.ExperimentGroupMapper;
import edu.wfust.learning.platform.mapper.ExperimentTaskMapper;
import edu.wfust.learning.platform.mapper.SysUserMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExperimentTaskService {
    private final ExperimentTaskMapper tasks; private final ExperimentGroupMapper groups; private final ExperimentAssignmentMapper assignments; private final SysUserMapper users; private final ObjectMapper json; private final Clock clock;
    public ExperimentTaskService(ExperimentTaskMapper tasks,ExperimentGroupMapper groups,ExperimentAssignmentMapper assignments,Clock clock){this(tasks,groups,assignments,null,new ObjectMapper(),clock);}
    @Autowired public ExperimentTaskService(ExperimentTaskMapper tasks,ExperimentGroupMapper groups,ExperimentAssignmentMapper assignments,SysUserMapper users,ObjectMapper json,Clock clock){this.tasks=tasks;this.groups=groups;this.assignments=assignments;this.users=users;this.json=json;this.clock=clock;}

    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public TaskView createTask(Long userId,CreateTaskRequest request){
        if(request==null||request.question()==null||request.question().isBlank())throw bad("实验题目不能为空");
        if("QWEN_MATRIX".equals(request.experimentType())&&request.includeAblations())throw bad("Qwen 矩阵任务不包含消融条件");
        var row=new ExperimentTaskEntity();row.setTitle(request.title().trim());row.setDescription(request.description()==null?"":request.description().trim());row.setExperimentType(request.experimentType());row.setQuestion(request.question().trim());row.setReferenceAnswer(request.referenceAnswer()==null?"":request.referenceAnswer().trim());row.setExpectedEntitiesJson(json.writeValueAsString(request.expectedEntities()==null?List.of():request.expectedEntities()));row.setIncludeAblations(request.includeAblations());row.setStatus("DRAFT");row.setCreatedBy(userId);tasks.insert(row);return view(row);
    }
    @PreAuthorize("hasRole('ADMIN')") public List<TaskView> listTasks(){return tasks.selectList(new QueryWrapper<ExperimentTaskEntity>().orderByDesc("id")).stream().map(this::view).toList();}
    @PreAuthorize("hasRole('ADMIN')") public List<GroupView> listGroups(Long taskId){task(taskId);return groups.selectList(new QueryWrapper<ExperimentGroupEntity>().eq("task_id",taskId).orderByAsc("sort_order","id")).stream().map(g->new GroupView(g.getId(),taskId,g.getName(),g.getConditionKey(),g.getSortOrder(),g.getTaskOrder())).toList();}
    @PreAuthorize("hasRole('ADMIN')") public List<AdminAssignmentView> listAssignments(Long taskId){task(taskId);var rows=assignments.findTaskAssignmentsForAdmin(taskId);return rows==null?List.of():rows.stream().map(r->new AdminAssignmentView(r.id(),r.taskId(),r.groupId(),r.userId(),r.username(),r.displayName(),r.groupName(),r.conditionKey(),r.taskOrder(),r.status(),r.assignedAt(),r.startedAt(),r.completedAt(),r.runCount())).toList();}
    @PreAuthorize("hasRole('ADMIN')") @Transactional public GroupView addGroup(Long taskId,GroupRequest request){task(taskId);if(request==null||request.taskOrder()==null||(!"A→B".equals(request.taskOrder().trim())&&!"B→A".equals(request.taskOrder().trim())))throw bad("实验顺序必须为 A→B 或 B→A");var group=new ExperimentGroupEntity();group.setTaskId(taskId);group.setName(request.name().trim());group.setConditionKey(request.conditionKey().trim());group.setSortOrder(request.sortOrder()==null?0:request.sortOrder());group.setTaskOrder(request.taskOrder().trim());groups.insert(group);return new GroupView(group.getId(),taskId,group.getName(),group.getConditionKey(),group.getSortOrder(),group.getTaskOrder());}
    @PreAuthorize("hasRole('ADMIN')") @Transactional public void assign(Long taskId,Long groupId,List<Long> userIds){task(taskId);var group=groups.selectById(groupId);if(group==null||!taskId.equals(group.getTaskId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"实验组不存在");if(userIds==null||userIds.isEmpty())throw bad("派发学生列表不能为空");if(userIds.size()>500)throw bad("单次派发学生数量不能超过 500");java.util.Set<Long> uniqueIds=new java.util.HashSet<>();for(Long userId:userIds){if(userId==null||userId<=0)throw bad("学生 ID 必须为正整数");if(!uniqueIds.add(userId))throw bad("派发学生列表包含重复 ID");}if(users!=null){for(Long userId:userIds){var user=users.selectById(userId);if(user==null||!"STUDENT".equals(user.getRole()))throw bad("实验任务只能分配给学生账号");}}for(Long userId:userIds){assignments.assign(taskId,groupId,userId);}}
    @PreAuthorize("hasRole('ADMIN')") @Transactional public TaskView setStatus(Long id,String status){if(!List.of("DRAFT","OPEN","CLOSED").contains(status))throw bad("任务状态无效");var row=task(id);row.setStatus(status);tasks.updateById(row);return view(row);}

    @PreAuthorize("hasRole('STUDENT')") public List<StudentAssignmentView> studentAssignments(Long userId){return assignments.findForUser(userId).stream().map(this::studentAssignmentView).toList();}
    @PreAuthorize("hasRole('STUDENT')") public AssignedRunSpec getAssignedRunSpec(Long userId, Long taskId){var task=task(taskId);if(!"OPEN".equals(task.getStatus()))throw bad("实验任务当前未开放");var row=assignments.findForUserAndTask(userId,taskId);if(row==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前账户未被分配此实验任务");if("COMPLETED".equals(row.getStatus()))throw bad("此实验任务已经完成");return new AssignedRunSpec(row.getId(),taskId,task.getQuestion(),task.getReferenceAnswer(),readList(task.getExpectedEntitiesJson()),task.getExperimentType(),Boolean.TRUE.equals(task.getIncludeAblations()));}
    @PreAuthorize("hasRole('STUDENT')") @Transactional public StudentAssignmentView startForStudent(Long userId,Long assignmentId){var row=assignment(assignmentId,userId);var task=task(row.getTaskId());if(!"OPEN".equals(task.getStatus()))throw bad("实验任务当前未开放");if("ASSIGNED".equals(row.getStatus())){assignments.start(assignmentId,LocalDateTime.now(clock));row.setStatus("IN_PROGRESS");}return studentAssignmentView(row);}
    @PreAuthorize("hasRole('STUDENT')") @Transactional public long bindRun(Long userId,Long taskId){var task=task(taskId);if(!"OPEN".equals(task.getStatus()))throw bad("实验任务当前未开放");var row=assignments.findForUserAndTask(userId,taskId);if(row==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前账户未被分配此实验任务");if("COMPLETED".equals(row.getStatus()))throw bad("此实验任务已经完成");if("ASSIGNED".equals(row.getStatus())){assignments.start(row.getId(),LocalDateTime.now(clock));row.setStatus("IN_PROGRESS");}return row.getId();}
    @PreAuthorize("hasRole('STUDENT')") @Transactional public void completeRun(Long userId,Long assignmentId,boolean successful){if(!successful)return;var row=assignment(assignmentId,userId);if("IN_PROGRESS".equals(row.getStatus()))assignments.complete(assignmentId,userId,LocalDateTime.now(clock));}
    @PreAuthorize("hasRole('ADMIN')") public String exportCsv(Long taskId){var builder=new StringBuilder("assignmentId,taskId,groupId,userId,status,taskTitle,taskStatus,groupName,conditionKey,runCount\n");for(var row:assignments.exportRows(taskId))builder.append(csv(row.id())).append(',').append(csv(row.taskId())).append(',').append(csv(row.groupId())).append(',').append(csv(row.userId())).append(',').append(csv(row.status())).append(',').append(csv(row.title())).append(',').append(csv(row.taskStatus())).append(',').append(csv(row.groupName())).append(',').append(csv(row.conditionKey())).append(',').append(csv(row.runCount())).append('\n');return builder.toString();}

    private TaskView view(ExperimentTaskEntity row){return new TaskView(row.getId(),row.getTitle(),row.getDescription(),row.getExperimentType(),row.getQuestion(),row.getReferenceAnswer(),readList(row.getExpectedEntitiesJson()),Boolean.TRUE.equals(row.getIncludeAblations()),row.getStatus(),row.getCreatedBy(),tasks.countGroups(row.getId()),tasks.countAssignments(row.getId()));}
    private StudentAssignmentView studentAssignmentView(ExperimentAssignmentMapper.AssignmentRow row){return new StudentAssignmentView(row.id(),row.taskId(),row.groupId(),row.status(),row.title(),row.description(),row.experimentType(),row.question(),row.taskStatus(),row.groupName(),row.conditionKey(),row.taskOrder(),row.startedAt(),row.completedAt());}
    private StudentAssignmentView studentAssignmentView(ExperimentAssignmentEntity row){var task=task(row.getTaskId());var group=groups.selectById(row.getGroupId());return new StudentAssignmentView(row.getId(),row.getTaskId(),row.getGroupId(),row.getStatus(),task.getTitle(),task.getDescription(),task.getExperimentType(),task.getQuestion(),task.getStatus(),group==null?"":group.getName(),group==null?"":group.getConditionKey(),group==null?null:group.getTaskOrder(),row.getStartedAt(),row.getCompletedAt());}
    private ExperimentTaskEntity task(Long id){var row=id==null?null:tasks.selectById(id);if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"实验任务不存在");return row;}
    private ExperimentAssignmentEntity assignment(Long id,Long userId){var row=assignments.selectById(id);if(row==null||!userId.equals(row.getUserId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"实验分配不存在");return row;}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private List<String> readList(String value){if(value==null||value.isBlank())return List.of();return json.readValue(value,new TypeReference<>(){});}
    private String csv(Object value){if(value==null)return "";String text=String.valueOf(value).replace("\"","\"\"");return text.contains(",")||text.contains("\n")?"\""+text+"\"":text;}
    public record CreateTaskRequest(@NotBlank @Size(max=160)String title,@Size(max=1000)String description,@NotBlank String experimentType,@NotBlank @Size(max=2000)String question,@Size(max=4000)String referenceAnswer,@Size(max=40)List<@NotBlank @Size(max=100)String> expectedEntities,Boolean includeAblations){public CreateTaskRequest{if(includeAblations==null)includeAblations=false;if(expectedEntities==null)expectedEntities=List.of();}}
    public record GroupRequest(@NotBlank @Size(max=100)String name,@NotBlank @Size(max=80)String conditionKey,@Positive Integer sortOrder,String taskOrder){ }
    public record TaskView(Long id,String title,String description,String experimentType,String question,String referenceAnswer,List<String> expectedEntities,boolean includeAblations,String status,Long createdBy,long groupCount,long assignmentCount){ }
    public record GroupView(Long id,Long taskId,String name,String conditionKey,Integer sortOrder,String taskOrder){
        public GroupView(Long id,Long taskId,String name,String conditionKey,Integer sortOrder){this(id,taskId,name,conditionKey,sortOrder,null);}
    }
    public record AssignedRunSpec(Long assignmentId,Long taskId,String question,String referenceAnswer,List<String> expectedEntities,String experimentType,boolean includeAblations){ }
    public record StudentAssignmentView(Long id,Long taskId,Long groupId,String status,String title,String description,String experimentType,String question,String taskStatus,String groupName,String conditionKey,String taskOrder,LocalDateTime startedAt,LocalDateTime completedAt){ }
    public record AdminAssignmentView(Long id,Long taskId,Long groupId,Long userId,String username,String displayName,String groupName,String conditionKey,String taskOrder,String status,LocalDateTime assignedAt,LocalDateTime startedAt,LocalDateTime completedAt,Long runCount){ }
}
