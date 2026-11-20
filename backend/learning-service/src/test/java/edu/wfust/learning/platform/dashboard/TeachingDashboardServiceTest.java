package edu.wfust.learning.platform.dashboard;

import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.TeachingDashboardMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeachingDashboardServiceTest {
    private final CourseMapper courses=mock(CourseMapper.class);
    private final TeachingDashboardMapper mapper=mock(TeachingDashboardMapper.class);
    private final TeachingDashboardService service=new TeachingDashboardService(courses,mapper,Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test void returnsAnonymousAggregatesAndWeakPoints(){
        var course=new CourseEntity();course.setId(1L);course.setName("软件工程");course.setEnabled(true);when(courses.selectById(1L)).thenReturn(course);
        when(mapper.defaultDays(1L)).thenReturn(7);
        when(mapper.overview(eq(1L),any(),any())).thenReturn(new TeachingDashboardMapper.Overview(4L,8L,3L,20L,15L,2L,62.5));
        when(mapper.dailyPractice(eq(1L),any(),any())).thenReturn(List.of(new TeachingDashboardMapper.DailyPractice(java.time.LocalDate.of(2026,11,18),3L,4L,3L)));
        when(mapper.dailyReading(eq(1L),any(),any())).thenReturn(List.of(new TeachingDashboardMapper.DailyReading(java.time.LocalDate.of(2026,11,18),2L)));
        when(mapper.weakPoints(1L)).thenReturn(List.of(new TeachingDashboardMapper.WeakPoint(2L,"数据流图",35.0,5L,3L)));
        var result=service.dashboard(null,1L);
        assertThat(result.overview().activeLearners()).isEqualTo(4);assertThat(result.overview().attempts()).isEqualTo(20);assertThat(result.weakPoints()).hasSize(1);assertThat(result.dailyActivity()).anyMatch(day->day.attempts()==4&&day.completedReads()==2);
    }
}

