package edu.wfust.learning.platform.experimenttask;

import edu.wfust.learning.platform.entity.ExperimentAssignmentEntity;
import edu.wfust.learning.platform.entity.ExperimentTaskEntity;
import edu.wfust.learning.platform.mapper.ExperimentAssignmentMapper;
import edu.wfust.learning.platform.mapper.ExperimentGroupMapper;
import edu.wfust.learning.platform.mapper.ExperimentTaskMapper;
import edu.wfust.learning.platform.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExperimentTaskServiceTest {
    private final ExperimentTaskMapper tasks = mock(ExperimentTaskMapper.class);
    private final ExperimentGroupMapper groups = mock(ExperimentGroupMapper.class);
    private final ExperimentAssignmentMapper assignments = mock(ExperimentAssignmentMapper.class);
    private final ExperimentTaskService service = new ExperimentTaskService(tasks, groups, assignments,
        Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void createsDraftTaskWithExplicitExperimentScope() {
        var result = service.createTask(5L, new ExperimentTaskService.CreateTaskRequest(
            "需求分析问答任务", "比较三种问答策略", "METHODS", "数据流图中加工如何命名？", "动宾短语", List.of("动宾短语"), false));

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.createdBy()).isEqualTo(5L);
        var inserted = org.mockito.ArgumentCaptor.forClass(ExperimentTaskEntity.class);
        verify(tasks).insert(inserted.capture());
        assertThat(inserted.getValue().getQuestion()).contains("加工");
        assertThat(inserted.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void bindingARealRunStartsAssignmentAndCompletionRequiresServerRun() {
        var task = new ExperimentTaskEntity(); task.setId(9L); task.setStatus("OPEN"); task.setQuestion("问题");
        when(tasks.selectById(9L)).thenReturn(task);
        var assignment = new ExperimentAssignmentEntity(); assignment.setId(21L); assignment.setTaskId(9L); assignment.setUserId(7L); assignment.setStatus("ASSIGNED");
        when(assignments.findForUserAndTask(7L, 9L)).thenReturn(assignment);
        when(assignments.selectById(21L)).thenReturn(assignment);

        long assignmentId = service.bindRun(7L, 9L);
        assertThat(assignmentId).isEqualTo(21L);
        verify(assignments).start(eq(21L), any());

        service.completeRun(7L, 21L, true);
        verify(assignments).complete(eq(21L), eq(7L), any());
    }

    @Test
    void cannotBindRunForAnotherUserOrClosedTask() {
        var task = new ExperimentTaskEntity(); task.setId(9L); task.setStatus("CLOSED");
        when(tasks.selectById(9L)).thenReturn(task);
        assertThatThrownBy(() -> service.bindRun(7L, 9L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(assignments);
    }

    @Test
    void studentAssignmentProjectionDoesNotExposeAnotherIdentity() {
        when(assignments.findForUser(7L)).thenReturn(List.of(new ExperimentAssignmentMapper.AssignmentRow(
            21L, 9L, 3L, 7L, "ASSIGNED", null, null, null, "任务", "说明", "METHODS", "问题", "参考", "OPEN", "组A", "METHODS")));
        var result = service.studentAssignments(7L);
        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(21L);
            assertThat(view.getClass().getRecordComponents()).noneMatch(component -> component.getName().equals("userId"));
        });
    }

    @Test
    void assignmentRejectsAdministratorAccounts() {
        var users = mock(SysUserMapper.class);
        var task = new ExperimentTaskEntity(); task.setId(9L); task.setStatus("OPEN");
        var group = new edu.wfust.learning.platform.entity.ExperimentGroupEntity(); group.setId(3L); group.setTaskId(9L);
        var admin = new edu.wfust.learning.platform.entity.SysUserEntity(); admin.setId(1L); admin.setRole("ADMIN");
        when(tasks.selectById(9L)).thenReturn(task); when(groups.selectById(3L)).thenReturn(group); when(users.selectById(1L)).thenReturn(admin);
        var guarded = new ExperimentTaskService(tasks, groups, assignments, users, new tools.jackson.databind.ObjectMapper(), Clock.systemUTC());

        assertThatThrownBy(() -> guarded.assign(9L, 3L, List.of(1L)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("只能分配给学生");
        verify(assignments, never()).assign(any(), any(), any());
    }
}
