package edu.wfust.learning.platform.dashboard;

import edu.wfust.learning.platform.mapper.TeachingDashboardMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeachingDashboardMapperRoleTest {

    private String getSelectSql(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = TeachingDashboardMapper.class.getMethod(methodName, paramTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join(" ", select.value());
    }

    @Test
    void overviewSubqueriesEnforceStudentRoleFilterExceptTotalSections() throws Exception {
        String sql = getSelectSql("overview", Long.class, java.time.LocalDateTime.class, java.time.LocalDateTime.class);

        assertThat(sql).contains("sys_user ua ON ua.id=a.user_id AND ua.role='STUDENT'");
        assertThat(sql).contains("sys_user up ON up.id=p.user_id AND up.role='STUDENT'");
        assertThat(sql).contains("sys_user ua2 ON ua2.id=a2.user_id AND ua2.role='STUDENT'");
        assertThat(sql).contains("sys_user ua3 ON ua3.id=a3.user_id AND ua3.role='STUDENT'");
        assertThat(sql).contains("sys_user uw ON uw.id=w.user_id AND uw.role='STUDENT'");
        assertThat(sql).contains("sys_user um ON um.id=m.user_id AND um.role='STUDENT'");

        // totalSections 属于课程章节静态总量，不应包含用户过滤
        assertThat(sql).contains("SELECT COUNT(*) FROM course_study_section s JOIN course c3 ON c3.id=s.course_id AND c3.enabled=TRUE");
    }

    @Test
    void dailyPracticeAndReadingEnforceStudentRoleFilter() throws Exception {
        String practiceSql = getSelectSql("dailyPractice", Long.class, java.time.LocalDateTime.class, java.time.LocalDateTime.class);
        assertThat(practiceSql).contains("sys_user u ON u.id=a.user_id AND u.role='STUDENT'");

        String readingSql = getSelectSql("dailyReading", Long.class, java.time.LocalDateTime.class, java.time.LocalDateTime.class);
        assertThat(readingSql).contains("sys_user u ON u.id=p.user_id AND u.role='STUDENT'");
    }

    @Test
    void weakPointsEnforcesStudentRoleFilterOnMastery() throws Exception {
        String weakSql = getSelectSql("weakPoints", Long.class);
        assertThat(weakSql).contains("SELECT m.* FROM knowledge_mastery m JOIN sys_user u ON u.id=m.user_id AND u.role='STUDENT'");
    }

    @Test
    void aggregateDtosDoNotExposeUserIdentityFields() {
        List<Class<?>> dtos = List.of(
                TeachingDashboardMapper.Overview.class,
                TeachingDashboardMapper.DailyPractice.class,
                TeachingDashboardMapper.DailyReading.class,
                TeachingDashboardMapper.WeakPoint.class
        );

        for (Class<?> dto : dtos) {
            assertThat(dto.isRecord()).isTrue();
            RecordComponent[] components = dto.getRecordComponents();
            List<String> names = Arrays.stream(components).map(RecordComponent::getName).toList();
            assertThat(names)
                    .doesNotContain("userId", "user_id", "username", "userName", "studentId", "studentName");
        }
    }
}
