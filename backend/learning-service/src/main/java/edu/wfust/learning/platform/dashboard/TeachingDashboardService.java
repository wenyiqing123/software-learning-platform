package edu.wfust.learning.platform.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.TeachingDashboardMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class TeachingDashboardService {
    private final CourseMapper courses;
    private final TeachingDashboardMapper mapper;
    private final Clock clock;
    public TeachingDashboardService(CourseMapper courses, TeachingDashboardMapper mapper, Clock clock){this.courses=courses;this.mapper=mapper;this.clock=clock;}

    public DashboardView dashboard(Integer requestedDays, Long courseId) {
        CourseEntity course = null;
        if (courseId != null) {
            course = courses.selectById(courseId);
            if (course == null || !Boolean.TRUE.equals(course.getEnabled())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"课程不存在或已停用");
        }
        int days = requestedDays == null ? (courseId == null ? 7 : defaultDays(courseId)) : requestedDays;
        if (days < 1 || days > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"看板统计范围必须在1到30天之间");
        LocalDate to=LocalDate.now(clock).plusDays(1), from=to.minusDays(days); LocalDateTime start=from.atStartOfDay(), end=to.atStartOfDay();
        var overview=mapper.overview(courseId,start,end); Map<LocalDate,ActivityBuilder> activity=new LinkedHashMap<>();
        for(int i=0;i<days;i++) activity.put(from.plusDays(i),new ActivityBuilder());
        mapper.dailyPractice(courseId,start,end).forEach(row->activity.computeIfAbsent(row.day(),ignored->new ActivityBuilder()).practice(row));
        mapper.dailyReading(courseId,start,end).forEach(row->activity.computeIfAbsent(row.day(),ignored->new ActivityBuilder()).reading(row));
        List<DailyActivity> daily=activity.entrySet().stream().map(entry->entry.getValue().view(entry.getKey())).toList();
        return new DashboardView(from,to.minusDays(1),days,courseId,course==null?"全部启用课程":course.getName(),
            new OverviewView(value(overview.activeLearners()),value(overview.totalSections()),value(overview.completedReads()),value(overview.attempts()),value(overview.correct()),value(overview.openWrong()),round(overview.averageMastery())),daily,
            mapper.weakPoints(courseId).stream().map(row->new WeakPointView(row.pointId(),row.pointName(),round(row.averageMastery()),value(row.attempts()),value(row.learners()))).toList());
    }
    private int defaultDays(Long courseId){Integer value=mapper.defaultDays(courseId);return value==null?7:value;}
    private long value(Long value){return value==null?0:value;}
    private double round(Double value){return value==null?0:Math.round(Math.max(0,Math.min(100,value))*100.0)/100.0;}
    private static final class ActivityBuilder {long learners,attempts,correct,reads;void practice(TeachingDashboardMapper.DailyPractice row){learners=Math.max(learners,value(row.activeLearners()));attempts+=value(row.attempts());correct+=value(row.correct());}void reading(TeachingDashboardMapper.DailyReading row){reads+=value(row.completedReads());}DailyActivity view(LocalDate day){return new DailyActivity(day,learners,attempts,correct,reads);}private long value(Long value){return value==null?0:value;}}
    public record DashboardView(LocalDate from,LocalDate to,int days,Long courseId,String courseName,OverviewView overview,List<DailyActivity> dailyActivity,List<WeakPointView> weakPoints){ }
    public record OverviewView(long activeLearners,long totalSections,long completedReads,long attempts,long correct,long openWrong,double averageMastery){ }
    public record DailyActivity(LocalDate date,long activeLearners,long attempts,long correct,long completedReads){ }
    public record WeakPointView(Long pointId,String pointName,double averageMastery,long attempts,long learners){ }
}

