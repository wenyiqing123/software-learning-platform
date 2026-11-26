package edu.wfust.learning.platform.evaluation;

import edu.wfust.learning.platform.entity.ExperimentAssignmentEntity;
import edu.wfust.learning.platform.mapper.EvaluationMapper;
import edu.wfust.learning.platform.mapper.ExperimentAssignmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvaluationServiceTest {
    private final EvaluationMapper mapper=mock(EvaluationMapper.class);
    private final ExperimentAssignmentMapper assignments=mock(ExperimentAssignmentMapper.class);
    private final EvaluationService service=new EvaluationService(mapper, JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test void requiresExplicitConsentBeforeCreatingSession() {
        assertThatThrownBy(() -> service.start(7L, new EvaluationService.StartRequest("CONTROL","TASK-1","v1",false)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mapper);
    }

    @Test void recordsOwnedMeasurementsAndCompletionWithinScale() {
        var clockNow = LocalDateTime.now(Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
        var session=new EvaluationMapper.SessionRow(11L,7L,"CONTROL","TASK-1","v1",null,clockNow.minusSeconds(420),null,null,null,null);
        when(mapper.insertSession(any())).thenAnswer(invocation -> { var row=invocation.<EvaluationMapper.SessionRow>getArgument(0); row.setId(11L); return 1; });
        when(mapper.findSession(7L,11L)).thenReturn(session);
        var started=service.start(7L,new EvaluationService.StartRequest("CONTROL","TASK-1","v1",true));
        assertThat(started.id()).isEqualTo(11L);
        service.measure(7L,11L,new EvaluationService.MeasureRequest("PRE",72.0,List.of("A","B")));
        service.measure(7L,11L,new EvaluationService.MeasureRequest("POST",86.0,List.of("A","A")));
        service.complete(7L,11L,new EvaluationService.CompleteRequest(420,7,"完成后感觉较轻松"));
        verify(mapper).upsertMeasurement(eq(7L),eq(11L),eq("PRE"),eq(72.0),anyString(),any());
        verify(mapper).upsertMeasurement(eq(7L),eq(11L),eq("POST"),eq(86.0),anyString(),any());
        verify(mapper).complete(eq(7L),eq(11L),eq(420),eq(7),eq("完成后感觉较轻松"),any());
    }

    @Test void rejectsOutOfRangeEffortAndScores() {
        assertThatThrownBy(() -> service.measure(7L,11L,new EvaluationService.MeasureRequest("PRE",101.0,List.of())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.complete(7L,11L,new EvaluationService.CompleteRequest(0,10,"")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void summaryContainsOnlyAggregatedRows() {
        when(mapper.summary(null)).thenReturn(List.of(new EvaluationMapper.SummaryRow("CONTROL",4L,70.0,84.0,14.0,5.5,360.0,4L)));
        var result=service.summary(null);
        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.conditionKey()).isEqualTo("CONTROL");
            assertThat(row.sampleCount()).isEqualTo(4L);
            assertThat(row.meanGain()).isEqualTo(14.0);
        });
    }

    @Test void acceptsDelayedMeasurementAndExportsAnonymousParticipantCode() {
        var session=new EvaluationMapper.SessionRow(11L,7L,"CONTROL","TASK-1","v1",null,null,null,null,null,null);
        when(mapper.findSession(7L,11L)).thenReturn(session);
        service.measure(7L,11L,new EvaluationService.MeasureRequest("DELAYED",91.0,List.of("A")));
        verify(mapper).upsertMeasurement(eq(7L),eq(11L),eq("DELAYED"),eq(91.0),anyString(),any());
        when(mapper.completedRows(null)).thenReturn(List.of(new EvaluationMapper.DetailRow(7L,"CONTROL","TASK-1","A→B",420,7,72.0,86.0,91.0,true)));
        String csv=service.exportAnonymized(null);
        assertThat(csv).contains("participantCode,conditionKey").doesNotContain("userId").contains("P-").contains("CONTROL").contains("91.0");
    }

    @Test void summaryKeepsExperimentalConditionsSeparate() {
        when(mapper.completedRows(null)).thenReturn(List.of(
                new EvaluationMapper.DetailRow(7L,"CONTROL","TASK-1","A",10,4,60.0,70.0,80.0,true),
                new EvaluationMapper.DetailRow(8L,"RANKED","TASK-2","B",20,6,70.0,85.0,90.0,false)));
        var result=service.summary(null);
        assertThat(result).extracting(EvaluationService.SummaryView::conditionKey).containsExactly("CONTROL","RANKED");
    }

    @Test void rejectsEvaluationStartForAnotherUsersAssignment() {
        var other = new ExperimentAssignmentEntity();
        other.setId(33L); other.setUserId(88L); other.setTaskId(9L);
        when(assignments.selectById(33L)).thenReturn(other);
        var secured = new EvaluationService(mapper, JsonMapper.builder().build(), Clock.systemUTC(), assignments);
        assertThatThrownBy(() -> secured.start(7L, new EvaluationService.StartRequest("CONTROL", "TASK-1", "A→B", 33L, "v1", true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("实验分配");
        verifyNoInteractions(mapper);
    }

    @Test void assignmentLinkedSessionAdoptsServerTaskOrderAndRejectsTampering() {
        var assignment = new ExperimentAssignmentEntity();
        assignment.setId(33L); assignment.setUserId(7L); assignment.setTaskId(9L);
        when(assignments.selectById(33L)).thenReturn(assignment);
        when(assignments.findDetailForUser(33L, 7L)).thenReturn(
                new ExperimentAssignmentMapper.AssignmentDetail(33L, 7L, 9L, 1L, "IN_PROGRESS", "OPEN", "B→A", null)
        );
        when(mapper.insertSession(any())).thenAnswer(invocation -> {
            var row = invocation.<EvaluationMapper.SessionRow>getArgument(0);
            row.setId(101L);
            return 1;
        });

        var secured = new EvaluationService(mapper, JsonMapper.builder().build(), Clock.systemUTC(), assignments);

        // 客户端篡改为 A→B 时拒绝 400
        assertThatThrownBy(() -> secured.start(7L, new EvaluationService.StartRequest("CONTROL", "TASK-1", "A→B", 33L, "v1", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

        // 客户端未传或传入匹配的 B→A 时，以服务端 B→A 为准持久化
        var session = secured.start(7L, new EvaluationService.StartRequest("CONTROL", "TASK-1", null, 33L, "v1", true));
        assertThat(session.taskOrder()).isEqualTo("B→A");
    }

    @Test void assignmentLinkedSessionSupportsBothServerOrders() {
        var assignment = new ExperimentAssignmentEntity();
        assignment.setId(34L); assignment.setUserId(7L); assignment.setTaskId(9L);
        when(assignments.selectById(34L)).thenReturn(assignment);
        when(assignments.findDetailForUser(34L, 7L)).thenReturn(
                new ExperimentAssignmentMapper.AssignmentDetail(34L, 7L, 9L, 1L, "ASSIGNED", "OPEN", "A→B", null)
        );
        when(mapper.insertSession(any())).thenAnswer(invocation -> {
            var row = invocation.<EvaluationMapper.SessionRow>getArgument(0);
            row.setId(102L);
            return 1;
        });

        var secured = new EvaluationService(mapper, JsonMapper.builder().build(), Clock.systemUTC(), assignments);
        var session = secured.start(7L, new EvaluationService.StartRequest("CONTROL", "TASK-1", "A→B", 34L, "v1", true));
        assertThat(session.taskOrder()).isEqualTo("A→B");
    }

    @Test void assignmentLinkedSessionRejectsClosedTaskCompletedAssignmentOrLegacyNullOrder() {
        var secured = new EvaluationService(mapper, JsonMapper.builder().build(), Clock.systemUTC(), assignments);

        // 场景 1：Task 未开放 (CLOSED)
        var assignment1 = new ExperimentAssignmentEntity();
        assignment1.setId(51L); assignment1.setUserId(7L); assignment1.setTaskId(9L);
        when(assignments.selectById(51L)).thenReturn(assignment1);
        when(assignments.findDetailForUser(51L, 7L)).thenReturn(
                new ExperimentAssignmentMapper.AssignmentDetail(51L, 7L, 9L, 1L, "IN_PROGRESS", "CLOSED", "A→B", null)
        );
        assertThatThrownBy(() -> secured.start(7L, new EvaluationService.StartRequest("CONTROL", "TASK-1", "A→B", 51L, "v1", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

        // 场景 2：Assignment 已完成 (COMPLETED)
        var assignment2 = new ExperimentAssignmentEntity();
        assignment2.setId(52L); assignment2.setUserId(7L); assignment2.setTaskId(9L);
        when(assignments.selectById(52L)).thenReturn(assignment2);
        when(assignments.findDetailForUser(52L, 7L)).thenReturn(
                new ExperimentAssignmentMapper.AssignmentDetail(52L, 7L, 9L, 1L, "COMPLETED", "OPEN", "A→B", null)
        );
        assertThatThrownBy(() -> secured.start(7L, new EvaluationService.StartRequest("CONTROL", "TASK-1", "A→B", 52L, "v1", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

        // 场景 3：服务端顺序为 legacy NULL
        var assignment3 = new ExperimentAssignmentEntity();
        assignment3.setId(53L); assignment3.setUserId(7L); assignment3.setTaskId(9L);
        when(assignments.selectById(53L)).thenReturn(assignment3);
        when(assignments.findDetailForUser(53L, 7L)).thenReturn(
                new ExperimentAssignmentMapper.AssignmentDetail(53L, 7L, 9L, 1L, "IN_PROGRESS", "OPEN", null, null)
        );
        assertThatThrownBy(() -> secured.start(7L, new EvaluationService.StartRequest("CONTROL", "TASK-1", null, 53L, "v1", true)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test void completionCalculatesAuthoritativeElapsedSecondsFromAssignmentServerStart() {
        var fixedClock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var fixedNow = LocalDateTime.now(fixedClock);
        var session = new EvaluationMapper.SessionRow(21L, 7L, "METHODS", "TASK-1", "A→B", 88L, "v1", fixedNow.minusMinutes(10), fixedNow.minusMinutes(10), null, null, null, null);
        when(mapper.findSession(7L, 21L)).thenReturn(session);
        when(mapper.hasMeasurement(7L, 21L, "PRE")).thenReturn(true);
        when(mapper.hasMeasurement(7L, 21L, "POST")).thenReturn(true);

        // assignment.started_at 设为 fixedNow 前 120 秒
        when(assignments.findDetailForUser(88L, 7L)).thenReturn(
                new ExperimentAssignmentMapper.AssignmentDetail(88L, 7L, 9L, 1L, "IN_PROGRESS", "OPEN", "A→B", fixedNow.minusSeconds(120))
        );

        var expMapper = mock(edu.wfust.learning.platform.mapper.AiExperimentMapper.class);
        var successRun = new edu.wfust.learning.platform.entity.AiExperimentEntity();
        successRun.setResultsJson("[{\"mode\":\"DIRECT\",\"answer\":\"正常答案\"}]");
        when(expMapper.selectList(any())).thenReturn(List.of(successRun));

        var serviceWithClock = new EvaluationService(mapper, JsonMapper.builder().build(), fixedClock, assignments, expMapper, mock(edu.wfust.learning.platform.experimenttask.ExperimentTaskService.class));
        // 客户端传入 elapsedSeconds = 1，服务端权威计算必须忽略 1，按真实 120 秒持久化
        serviceWithClock.complete(7L, 21L, new EvaluationService.CompleteRequest(1, 5, "很好"));
        verify(mapper).complete(eq(7L), eq(21L), eq(120), eq(5), eq("很好"), eq(fixedNow));
    }

    @Test void completionCalculatesAuthoritativeElapsedSecondsForUnassignedSession() {
        var fixedClock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var fixedNow = LocalDateTime.now(fixedClock);
        var session = new EvaluationMapper.SessionRow(22L, 7L, "METHODS", "TASK-1", "A→B", null, "v1", fixedNow.minusMinutes(5), fixedNow.minusSeconds(300), null, null, null, null);
        when(mapper.findSession(7L, 22L)).thenReturn(session);

        var serviceWithClock = new EvaluationService(mapper, JsonMapper.builder().build(), fixedClock, assignments);
        // 客户端传入 elapsedSeconds = 999，unassigned 使用 session.startedAt 算出的 300 秒
        serviceWithClock.complete(7L, 22L, new EvaluationService.CompleteRequest(999, 6, "非实验分配"));
        verify(mapper).complete(eq(7L), eq(22L), eq(300), eq(6), eq("非实验分配"), eq(fixedNow));
    }

    @Test void completionRejectsMissingStartOrOutOfRangeDurationOrNonOwner() {
        var fixedClock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var fixedNow = LocalDateTime.now(fixedClock);
        var serviceWithClock = new EvaluationService(mapper, JsonMapper.builder().build(), fixedClock, assignments);

        // 场景 1：超过 24 小时（86401 秒）拒绝 400
        var sessionOver24h = new EvaluationMapper.SessionRow(23L, 7L, "METHODS", "TASK-1", "A→B", null, "v1", null, fixedNow.minusSeconds(86401), null, null, null, null);
        when(mapper.findSession(7L, 23L)).thenReturn(sessionOver24h);
        assertThatThrownBy(() -> serviceWithClock.complete(7L, 23L, new EvaluationService.CompleteRequest(100, 5, "")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

        // 场景 2：缺少开始时间 (startedAt 为 null) 拒绝 400
        var sessionNullStart = new EvaluationMapper.SessionRow(24L, 7L, "METHODS", "TASK-1", "A→B", null, "v1", null, null, null, null, null, null);
        when(mapper.findSession(7L, 24L)).thenReturn(sessionNullStart);
        assertThatThrownBy(() -> serviceWithClock.complete(7L, 24L, new EvaluationService.CompleteRequest(100, 5, "")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

        // 场景 3：owner 不匹配或 session 不存在拒绝 404
        when(mapper.findSession(7L, 999L)).thenReturn(null);
        assertThatThrownBy(() -> serviceWithClock.complete(7L, 999L, new EvaluationService.CompleteRequest(100, 5, "")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test void mineRestoresCompletedPhasesForOwnedSessions() {
        var row1 = new EvaluationMapper.SessionRow(10L, 7L, "METHODS", "TASK-1", "A→B", 88L, "v1", null, null, null, null, null, null);
        var row2 = new EvaluationMapper.SessionRow(20L, 7L, "METHODS", "TASK-2", "B→A", null, "v1", null, null, null, null, null, null);
        when(mapper.listOwned(7L)).thenReturn(List.of(row1, row2));
        when(mapper.listOwnedPhaseStatuses(7L)).thenReturn(List.of(
                new EvaluationMapper.PhaseRow(10L, "PRE"),
                new EvaluationMapper.PhaseRow(10L, "POST"),
                new EvaluationMapper.PhaseRow(10L, "DELAYED"),
                new EvaluationMapper.PhaseRow(20L, "PRE")
        ));

        var result = service.mine(7L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).completedPhases()).containsExactly("PRE", "POST", "DELAYED");
        assertThat(result.get(1).completedPhases()).containsExactly("PRE");
    }

    @Test void assignmentLinkedCompletionRequiresPrePostAndSuccessfulRun() {
        var fixedClock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
        var fixedNow = LocalDateTime.now(fixedClock);
        var session = new EvaluationMapper.SessionRow(30L, 7L, "METHODS", "TASK-1", "A→B", 88L, "v1", null, fixedNow.minusMinutes(5), null, null, null, null);
        when(mapper.findSession(7L, 30L)).thenReturn(session);
        when(assignments.findDetailForUser(88L, 7L)).thenReturn(
                new ExperimentAssignmentMapper.AssignmentDetail(88L, 7L, 9L, 1L, "IN_PROGRESS", "OPEN", "A→B", fixedNow.minusSeconds(300))
        );

        var expMapper = mock(edu.wfust.learning.platform.mapper.AiExperimentMapper.class);
        var tService = mock(edu.wfust.learning.platform.experimenttask.ExperimentTaskService.class);
        var fullService = new EvaluationService(mapper, JsonMapper.builder().build(), fixedClock, assignments, expMapper, tService);

        // 缺 PRE -> 409
        when(mapper.hasMeasurement(7L, 30L, "PRE")).thenReturn(false);
        when(mapper.hasMeasurement(7L, 30L, "POST")).thenReturn(true);
        assertThatThrownBy(() -> fullService.complete(7L, 30L, new EvaluationService.CompleteRequest(10, 5, "")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

        // 缺 POST -> 409
        when(mapper.hasMeasurement(7L, 30L, "PRE")).thenReturn(true);
        when(mapper.hasMeasurement(7L, 30L, "POST")).thenReturn(false);
        assertThatThrownBy(() -> fullService.complete(7L, 30L, new EvaluationService.CompleteRequest(10, 5, "")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

        // 缺 successful run (run 列表为空) -> 409
        when(mapper.hasMeasurement(7L, 30L, "PRE")).thenReturn(true);
        when(mapper.hasMeasurement(7L, 30L, "POST")).thenReturn(true);
        when(expMapper.selectList(any())).thenReturn(List.of());
        assertThatThrownBy(() -> fullService.complete(7L, 30L, new EvaluationService.CompleteRequest(10, 5, "")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

        // 缺 successful run (run 只有 error) -> 409
        var failedRun = new edu.wfust.learning.platform.entity.AiExperimentEntity();
        failedRun.setResultsJson("[{\"error\":\"模型超时\"}]");
        when(expMapper.selectList(any())).thenReturn(List.of(failedRun));
        assertThatThrownBy(() -> fullService.complete(7L, 30L, new EvaluationService.CompleteRequest(10, 5, "")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));

        // 全部满足 -> 成功完成并调用 taskService.completeRun
        var successRun = new edu.wfust.learning.platform.entity.AiExperimentEntity();
        successRun.setResultsJson("[{\"mode\":\"DIRECT\",\"answer\":\"正常答案\"}]");
        when(expMapper.selectList(any())).thenReturn(List.of(successRun));

        fullService.complete(7L, 30L, new EvaluationService.CompleteRequest(10, 5, "完成总结"));
        verify(mapper).complete(eq(7L), eq(30L), eq(300), eq(5), eq("完成总结"), eq(fixedNow));
        verify(tService).completeRun(7L, 88L, true);
    }

    @Test void abandonsUncompletedOwnedSessionAndBlocksAbandoningCompleted() {
        var uncompleted = new EvaluationMapper.SessionRow(40L, 7L, "METHODS", "TASK-1", "A→B", null, "v1", null, null, null, null, null, null);
        when(mapper.findSession(7L, 40L)).thenReturn(uncompleted);
        service.abandon(7L, 40L);
        verify(mapper).deleteMeasurements(7L, 40L);
        verify(mapper).deleteUncompletedSession(7L, 40L);

        var completed = new EvaluationMapper.SessionRow(41L, 7L, "METHODS", "TASK-1", "A→B", null, "v1", null, null, LocalDateTime.now(), 120, 5, "ok");
        when(mapper.findSession(7L, 41L)).thenReturn(completed);
        assertThatThrownBy(() -> service.abandon(7L, 41L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test void summaryExposesDelayedStddevAndIqrWhenDelayedScoresExist() {
        when(mapper.completedRows(null)).thenReturn(List.of(
                new EvaluationMapper.DetailRow(7L, "METHODS", "TASK-1", "A→B", 100, 4, 60.0, 80.0, 70.0, true),
                new EvaluationMapper.DetailRow(8L, "METHODS", "TASK-1", "A→B", 120, 6, 70.0, 90.0, 90.0, true)
        ));
        var rows = service.summary(null);
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.meanDelayedScore()).isEqualTo(80.0);
            assertThat(r.stddevDelayedScore()).isEqualTo(10.0);
            assertThat(r.iqrDelayedScore()).isEqualTo(10.0);
        });
    }
}
