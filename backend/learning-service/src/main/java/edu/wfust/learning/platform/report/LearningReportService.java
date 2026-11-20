package edu.wfust.learning.platform.report;

import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.LearningReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningReportService {
    private final LearningReportMapper mapper;
    private final CourseMapper courses;
    private final Clock clock;
    public LearningReportService(LearningReportMapper mapper, CourseMapper courses, Clock clock) { this.mapper=mapper; this.courses=courses; this.clock=clock; }

    public ReportView report(long userId, int days, Long courseId) {
        if (days < 1 || days > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报告时间范围必须在1到30天之间");
        String courseName = "全部启用课程";
        if (courseId != null) {
            CourseEntity course = courses.selectById(courseId);
            if (course == null || !Boolean.TRUE.equals(course.getEnabled())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在或已停用");
            courseName = course.getName();
        }
        LocalDate toDate = LocalDate.now(clock).plusDays(1);
        LocalDate fromDate = toDate.minusDays(days);
        LocalDateTime start = fromDate.atStartOfDay(), end = toDate.atStartOfDay();
        var reading = mapper.reading(userId, courseId, start, end);
        var practice = mapper.practice(userId, courseId, start, end);
        var reviews = mapper.reviews(userId, courseId, start, end);
        var plans = mapper.plans(userId, fromDate, toDate);
        Map<LocalDate, ActivityBuilder> activity = new LinkedHashMap<>();
        for (int i=0;i<days;i++) activity.put(fromDate.plusDays(i), new ActivityBuilder());
        mapper.dailyPractice(userId, courseId, start, end).forEach(row -> activity.computeIfAbsent(row.day(), ignored -> new ActivityBuilder()).practice(row));
        mapper.dailyReading(userId, courseId, start, end).forEach(row -> activity.computeIfAbsent(row.day(), ignored -> new ActivityBuilder()).reading(row));
        List<DailyActivity> daily = activity.entrySet().stream().map(entry -> entry.getValue().view(entry.getKey())).toList();
        return new ReportView(fromDate, toDate.minusDays(1), days, courseId, courseName,
            new ReadingView(value(reading.totalSections()), value(reading.completedSections()), value(reading.completedInPeriod())),
            practiceView(practice), reviewView(reviews), planView(plans), daily,
            mapper.chapters(userId, courseId, start, end).stream().map(row -> new ChapterView(row.chapterId(), row.chapterName(), value(row.totalSections()), value(row.completedSections()), value(row.completedInPeriod()))).toList(),
            mapper.knowledge(userId, courseId).stream().map(row -> new KnowledgeView(row.pointId(), row.pointName(), round(row.mastery()), value(row.attempts()), value(row.correct()), value(row.dueReviewCount()))).toList());
    }

    public byte[] csv(ReportView report) {
        StringBuilder csv = new StringBuilder("\uFEFF类型,名称,掌握度,答题次数,答对次数,待复习,补充说明\n");
        csv.append(row("报告", report.courseName(), "", "", "", "", report.from()+" 至 "+report.to())).append('\n');
        for (ChapterView chapter : report.chapters()) csv.append(row("章节", chapter.chapterName(), "", chapter.completedSections()+"/"+chapter.totalSections(), "", "", "周期完成 "+chapter.completedInPeriod()+" 节")).append('\n');
        for (KnowledgeView point : report.knowledge()) csv.append(row("知识点", point.pointName(), point.mastery(), point.attempts(), point.correct(), point.dueReviewCount(), "掌握度为答题记录估算")).append('\n');
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String row(Object... values) { List<String> cells=new ArrayList<>(); for(Object value:values){String text=value==null?"":String.valueOf(value).replace("\"","\"\"");cells.add("\""+text+"\"");} return String.join(",",cells); }
    private PracticeView practiceView(LearningReportMapper.PracticeSummary row){long attempts=value(row.attempts()),correct=value(row.correct());return new PracticeView(attempts,correct,attempts==0?0:Math.round(correct*10000.0/attempts)/100.0);}
    private ReviewView reviewView(LearningReportMapper.ReviewSummary row){long reviews=value(row.reviews()),correct=value(row.correct());return new ReviewView(reviews,correct,reviews==0?0:Math.round(correct*10000.0/reviews)/100.0);}
    private PlanView planView(LearningReportMapper.PlanSummary row){return new PlanView(value(row.totalTasks()),value(row.completedTasks()),value(row.plannedMinutes()),value(row.completedMinutes()));}
    private static long value(Long value){return value==null?0:value;}
    private static double round(Double value){return value==null?0:Math.round(Math.max(0,Math.min(100,value))*100.0)/100.0;}

    private static final class ActivityBuilder { long attempts,correct,reading; void practice(LearningReportMapper.DailyPractice row){attempts=value(row.attempts());correct=value(row.correct());} void reading(LearningReportMapper.DailyReading row){reading=value(row.completed());} DailyActivity view(LocalDate date){return new DailyActivity(date,attempts,correct,reading);} }
    public record ReportView(LocalDate from, LocalDate to, int days, Long courseId, String courseName, ReadingView reading, PracticeView practice, ReviewView reviews, PlanView plans, List<DailyActivity> dailyActivity, List<ChapterView> chapters, List<KnowledgeView> knowledge) { }
    public record ReadingView(long totalSections,long completedSections,long completedInPeriod) { }
    public record PracticeView(long attempts,long correct,double accuracy) { }
    public record ReviewView(long reviews,long correct,double accuracy) { }
    public record PlanView(long totalTasks,long completedTasks,long plannedMinutes,long completedMinutes) { }
    public record DailyActivity(LocalDate date,long attempts,long correct,long readingCompleted) { }
    public record ChapterView(Long chapterId,String chapterName,long totalSections,long completedSections,long completedInPeriod) { }
    public record KnowledgeView(Long pointId,String pointName,double mastery,long attempts,long correct,long dueReviewCount) { }
}
