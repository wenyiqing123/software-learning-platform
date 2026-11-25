package edu.wfust.learning.platform.knowledge;

import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.MasteryMapMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MasteryMapServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final CourseMapper courses = mock(CourseMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final KnowledgeMasteryMapper mastery = mock(KnowledgeMasteryMapper.class);
    private final MasteryMapMapper due = mock(MasteryMapMapper.class);
    private final MasteryMapService service = new MasteryMapService(courses, points, mastery, due, JsonMapper.builder().build(), FIXED_CLOCK);

    @Test
    void exposesUserMasteryDueReviewsAndPrerequisiteBlocking() {
        var course = new CourseEntity(); course.setId(1L); course.setName("软件工程"); course.setEnabled(true); when(courses.selectById(1L)).thenReturn(course);
        var prerequisite = point(1L, "需求分析", null, 1);
        var target = point(2L, "数据流图", "[1]", 2);
        when(points.selectList(any())).thenReturn(List.of(prerequisite, target));
        var masteryRow = new KnowledgeMasteryEntity(); masteryRow.setKnowledgePointId(1L); masteryRow.setMasteryScore(new BigDecimal("80")); masteryRow.setAttemptCount(5); masteryRow.setCorrectCount(4);
        when(mastery.selectList(any())).thenReturn(List.of(masteryRow));
        var dueRow = new MasteryMapMapper.DueCount(); dueRow.setPointId(2L); dueRow.setDueCount(2L); when(due.dueCounts(9L)).thenReturn(List.of(dueRow));

        var result = service.map(1L, 9L);

        assertThat(result.nodes()).extracting(MasteryMapService.Node::status).containsExactly("MASTERED", "UP_NEXT");
        assertThat(result.nodes().get(1).dueReviewCount()).isEqualTo(2L);
        assertThat(result.edges()).containsExactly(new MasteryMapService.Edge(1L, 2L, "先修"));
    }

    @Test
    void aggregatesAllEnabledCoursesWhenCourseFilterIsAbsent() {
        var first = course(1L, "软件工程一");
        var second = course(2L, "软件工程二");
        when(courses.selectList(any())).thenReturn(List.of(first, second));
        when(points.selectList(any())).thenReturn(List.of(point(1L, "需求分析", null, 1), point(2L, "系统设计", null, 1)));
        when(mastery.selectList(any())).thenReturn(List.of());
        when(due.dueCounts(9L)).thenReturn(List.of());

        var result = service.map(null, 9L);

        assertThat(result.courseId()).isNull();
        assertThat(result.courseName()).isEqualTo("全部启用课程");
        assertThat(result.nodes()).extracting(MasteryMapService.Node::id).containsExactly(1L, 2L);
    }

    @Test
    void computesDualValueRetentionAndKeepsDependentsUnlockedAfterDecay() {
        var course = course(1L, "软件工程");
        when(courses.selectById(1L)).thenReturn(course);
        var prerequisite = point(1L, "需求分析", null, 1);
        var target = point(2L, "数据流图", "[1]", 2);
        when(points.selectList(any())).thenReturn(List.of(prerequisite, target));

        var masteryRow = new KnowledgeMasteryEntity();
        masteryRow.setKnowledgePointId(1L);
        masteryRow.setMasteryScore(new BigDecimal("80.00"));
        masteryRow.setPeakMastery(new BigDecimal("85.00"));
        masteryRow.setAttemptCount(5);
        masteryRow.setCorrectCount(4);
        masteryRow.setLastStudiedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(14));
        when(mastery.selectList(any())).thenReturn(List.of(masteryRow));
        when(due.dueCounts(9L)).thenReturn(List.of());

        var result = service.map(1L, 9L);
        var prereqNode = result.nodes().get(0);
        var nextNode = result.nodes().get(1);

        assertThat(prereqNode.status()).isEqualTo("MASTERED");
        assertThat(prereqNode.peakMastery()).isEqualTo(85.0);
        assertThat(prereqNode.effectiveMastery()).isEqualTo(40.0);
        assertThat(prereqNode.daysSinceLastPractice()).isEqualTo(14L);
        assertThat(prereqNode.reviewUrgency()).isEqualTo("DUE");
        assertThat(nextNode.status()).isEqualTo("UP_NEXT");
    }

    private KnowledgePointEntity point(Long id, String name, String prerequisites, int order) {
        var row = new KnowledgePointEntity(); row.setId(id); row.setCourseId(1L); row.setName(name); row.setGraphEntity(name); row.setPrerequisitesJson(prerequisites); row.setSortOrder(order); row.setEnabled(true); return row;
    }

    private CourseEntity course(Long id, String name) {
        var row = new CourseEntity(); row.setId(id); row.setName(name); row.setEnabled(true); return row;
    }
}
