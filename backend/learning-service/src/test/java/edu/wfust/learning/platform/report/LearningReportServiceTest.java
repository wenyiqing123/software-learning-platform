package edu.wfust.learning.platform.report;

import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.LearningReportMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearningReportServiceTest {
    private final LearningReportMapper mapper=mock(LearningReportMapper.class);
    private final CourseMapper courses=mock(CourseMapper.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final LearningReportService service=new LearningReportService(mapper,courses,clock);

    @Test
    void buildsPeriodChapterKnowledgeAndCsvReportWithoutInventingMetrics(){
        when(mapper.reading(anyLong(),isNull(),any(),any())).thenReturn(new LearningReportMapper.ReadingSummary(8L,3L,1L));
        when(mapper.practice(anyLong(),isNull(),any(),any())).thenReturn(new LearningReportMapper.PracticeSummary(4L,3L));
        when(mapper.reviews(anyLong(),isNull(),any(),any())).thenReturn(new LearningReportMapper.ReviewSummary(2L,1L));
        when(mapper.plans(anyLong(),any(),any())).thenReturn(new LearningReportMapper.PlanSummary(3L,2L,45L,30L));
        when(mapper.dailyPractice(anyLong(),isNull(),any(),any())).thenReturn(List.of(new LearningReportMapper.DailyPractice(java.time.LocalDate.of(2026,11,18),2L,1L)));
        when(mapper.dailyReading(anyLong(),isNull(),any(),any())).thenReturn(List.of(new LearningReportMapper.DailyReading(java.time.LocalDate.of(2026,11,18),1L)));
        when(mapper.chapters(anyLong(),isNull(),any(),any())).thenReturn(List.of(new LearningReportMapper.ChapterSummary(1L,"需求分析",4L,2L,1L)));
        when(mapper.knowledge(anyLong(),isNull())).thenReturn(List.of(new LearningReportMapper.KnowledgeSummary(1L,"需求分析",75.0,4L,3L,1L)));

        var report=service.report(7L,7,null);
        assertThat(report.reading().completedInPeriod()).isEqualTo(1);
        assertThat(report.practice().accuracy()).isEqualTo(75.0);
        assertThat(report.dailyActivity().stream().filter(row -> row.date().toString().equals("2026-11-18")).findFirst().orElseThrow().readingCompleted()).isEqualTo(1);
        assertThat(new String(service.csv(report),java.nio.charset.StandardCharsets.UTF_8)).contains("需求分析","待复习");
    }
}
