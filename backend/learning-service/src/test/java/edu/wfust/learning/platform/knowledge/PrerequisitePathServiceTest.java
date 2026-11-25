package edu.wfust.learning.platform.knowledge;

import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PrerequisitePathServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final CourseMapper courses = mock(CourseMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final KnowledgeMasteryMapper mastery = mock(KnowledgeMasteryMapper.class);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void returnsTransitivePrerequisitesBeforeTargetWithPersonalStatuses() {
        var course = course(1L, true);
        var basics = point(1L, 1L, "需求分析", null, 1);
        var design = point(2L, 1L, "系统设计", "[1]", 2);
        var target = point(3L, 1L, "实现", "[2]", 3);
        when(points.selectById(3L)).thenReturn(target);
        when(courses.selectById(1L)).thenReturn(course);
        when(points.selectList(any())).thenReturn(List.of(target, design, basics));
        when(mastery.selectList(any())).thenReturn(List.of(score(1L, 80), score(2L, 30)));

        var path = service().path(3L, 9L);

        assertThat(path.nodes()).extracting(PrerequisitePathService.PathNode::id).containsExactly(1L, 2L, 3L);
        assertThat(path.nodes()).extracting(PrerequisitePathService.PathNode::status)
                .containsExactly("MASTERED", "IN_PROGRESS", "BLOCKED");
        assertThat(path.nodes().getLast().prerequisiteIds()).containsExactly(2L);
        assertThat(path.cycleDetected()).isFalse();
    }

    @Test
    void targetWithoutPrerequisitesIsUpNextAndAtThresholdIsMastered() {
        var target = point(5L, 1L, "验证", "[]", 1);
        when(points.selectById(5L)).thenReturn(target);
        when(courses.selectById(1L)).thenReturn(course(1L, true));
        when(points.selectList(any())).thenReturn(List.of(target));
        when(mastery.selectList(any())).thenReturn(List.of(score(5L, 60)));

        var path = service().path(5L, 9L);

        assertThat(path.nodes()).singleElement().satisfies(node -> assertThat(node.status()).isEqualTo("MASTERED"));
    }

    @Test
    void ignoresInvalidAndCrossCourseReferencesAndTerminatesOnCycles() {
        var target = point(1L, 1L, "目标", "[2,2,99,4]", 1);
        var dependency = point(2L, 1L, "前置", "[1]", 2);
        var crossCourse = point(4L, 2L, "其他课程", "[]", 1);
        when(points.selectById(1L)).thenReturn(target);
        when(courses.selectById(1L)).thenReturn(course(1L, true));
        when(points.selectList(any())).thenReturn(List.of(target, dependency, crossCourse));
        when(mastery.selectList(any())).thenReturn(List.of());

        var path = service().path(1L, 9L);

        assertThat(path.nodes()).extracting(PrerequisitePathService.PathNode::id).containsExactly(2L, 1L);
        assertThat(path.nodes().getLast().prerequisiteIds()).containsExactly(2L);
        assertThat(path.cycleDetected()).isTrue();
    }

    @Test
    void rejectsMissingOrDisabledTargetAndCourse() {
        when(points.selectById(90L)).thenReturn(null);
        assertThatThrownBy(() -> service().path(90L, 9L)).isInstanceOf(ResponseStatusException.class);
        when(points.selectById(91L)).thenReturn(point(91L, 1L, "停用", "[]", 1, false));
        assertThatThrownBy(() -> service().path(91L, 9L)).isInstanceOf(ResponseStatusException.class);
        when(points.selectById(92L)).thenReturn(point(92L, 2L, "课程停用", "[]", 1));
        when(courses.selectById(2L)).thenReturn(course(2L, false));
        assertThatThrownBy(() -> service().path(92L, 9L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void keepsPrerequisiteUnlockedByPeakMasteryEvenWhenEffectiveRetentionDecayedBelowThreshold() {
        var prereq = point(1L, 1L, "需求分析", null, 1);
        var target = point(2L, 1L, "数据流图", "[1]", 2);
        when(points.selectById(2L)).thenReturn(target);
        when(courses.selectById(1L)).thenReturn(course(1L, true));
        when(points.selectList(any())).thenReturn(List.of(prereq, target));

        var prereqRow = score(1L, 45);
        prereqRow.setPeakMastery(BigDecimal.valueOf(75));
        prereqRow.setLastStudiedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(14));
        when(mastery.selectList(any())).thenReturn(List.of(prereqRow));

        var path = service().path(2L, 9L);

        assertThat(path.nodes().get(0).status()).isEqualTo("MASTERED");
        assertThat(path.nodes().get(0).peakMastery()).isEqualTo(75.0);
        assertThat(path.nodes().get(0).effectiveMastery()).isEqualTo(22.5);
        assertThat(path.nodes().get(0).daysSinceLastPractice()).isEqualTo(14L);
        assertThat(path.nodes().get(0).reviewUrgency()).isEqualTo("DUE");
        // 先修峰值 75 >= 60，目标知识点保持 UP_NEXT 解锁态而非 BLOCKED
        assertThat(path.nodes().get(1).status()).isEqualTo("UP_NEXT");
    }

    private PrerequisitePathService service() {
        return new PrerequisitePathService(courses, points, mastery, json, FIXED_CLOCK);
    }

    private CourseEntity course(Long id, boolean enabled) {
        var value = new CourseEntity();
        value.setId(id);
        value.setEnabled(enabled);
        return value;
    }

    private KnowledgePointEntity point(Long id, Long courseId, String name, String prerequisites, int order) {
        return point(id, courseId, name, prerequisites, order, true);
    }

    private KnowledgePointEntity point(Long id, Long courseId, String name, String prerequisites, int order, boolean enabled) {
        var value = new KnowledgePointEntity();
        value.setId(id);
        value.setCourseId(courseId);
        value.setChapterId(10L + id);
        value.setName(name);
        value.setGraphEntity(name);
        value.setPrerequisitesJson(prerequisites);
        value.setSortOrder(order);
        value.setEnabled(enabled);
        return value;
    }

    private KnowledgeMasteryEntity score(Long pointId, int value) {
        var row = new KnowledgeMasteryEntity();
        row.setKnowledgePointId(pointId);
        row.setMasteryScore(BigDecimal.valueOf(value));
        return row;
    }
}
