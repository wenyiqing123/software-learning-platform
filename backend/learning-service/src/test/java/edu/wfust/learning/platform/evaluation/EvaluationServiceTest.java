package edu.wfust.learning.platform.evaluation;

import edu.wfust.learning.platform.entity.ExperimentAssignmentEntity;
import edu.wfust.learning.platform.mapper.EvaluationMapper;
import edu.wfust.learning.platform.mapper.ExperimentAssignmentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
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
        var session=new EvaluationMapper.SessionRow(11L,7L,"CONTROL","TASK-1","v1",null,null,null,null,null,null);
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
}
