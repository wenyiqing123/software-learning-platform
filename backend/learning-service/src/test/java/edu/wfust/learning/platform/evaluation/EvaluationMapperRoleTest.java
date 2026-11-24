package edu.wfust.learning.platform.evaluation;

import edu.wfust.learning.platform.mapper.EvaluationMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationMapperRoleTest {

    private String getSelectSql(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = EvaluationMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join(" ", select.value());
    }

    @Test
    void summaryQueryEnforcesStudentRoleJoin() throws Exception {
        String sql = getSelectSql("summary", String.class);
        assertThat(sql).contains("sys_user u ON u.id=s.user_id AND u.role='STUDENT'");
    }

    @Test
    void completedRowsQueryEnforcesStudentRoleJoin() throws Exception {
        String sql = getSelectSql("completedRows", String.class);
        assertThat(sql).contains("sys_user u ON u.id=s.user_id AND u.role='STUDENT'");
    }

    @Test
    void publicSummaryViewDoesNotExposeUserIdentityFields() {
        Class<?> summaryClass = EvaluationService.SummaryView.class;
        assertThat(summaryClass.isRecord()).isTrue();
        RecordComponent[] components = summaryClass.getRecordComponents();
        List<String> names = Arrays.stream(components).map(RecordComponent::getName).toList();
        assertThat(names).doesNotContain("userId", "user_id", "username", "userName", "studentId", "studentName");
    }

    @Test
    void csvExportHeaderDoesNotContainUserIdOrUsername() {
        EvaluationMapper mapper = mock(EvaluationMapper.class);
        when(mapper.completedRows(null)).thenReturn(List.of(
                new EvaluationMapper.DetailRow(10L, "CONTROL", "TASK-1", "A→B", 300, 5, 70.0, 85.0, 90.0, true)
        ));
        EvaluationService service = new EvaluationService(mapper, JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
        String csv = service.exportAnonymized(null);
        String header = csv.lines().findFirst().orElse("");

        assertThat(header).contains("participantCode");
        assertThat(header).doesNotContain("userId", "user_id", "username", "userName");
    }
}
