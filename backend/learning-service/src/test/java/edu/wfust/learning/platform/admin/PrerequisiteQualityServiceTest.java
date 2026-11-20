package edu.wfust.learning.platform.admin;

import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PrerequisiteQualityServiceTest {
    private final CourseMapper courses = mock(CourseMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void reportsInvalidReferencesAndDuplicateIdsWithoutChangingRows() {
        when(courses.selectById(1L)).thenReturn(course(1L));
        var first = point(1L, 1L, "需求分析", "[2,2,99]");
        var second = point(2L, 1L, "系统设计", "[]");
        when(points.selectList(any())).thenReturn(List.of(first, second));

        var report = service().quality(1L);

        assertThat(report.validEdgeCount()).isEqualTo(1);
        assertThat(report.issues()).extracting(PrerequisiteQualityService.Issue::code)
                .contains("DUPLICATE_REFERENCE", "MISSING_REFERENCE");
        assertThat(first.getPrerequisitesJson()).isEqualTo("[2,2,99]");
    }

    @Test
    void reportsSelfCrossCourseAndCycles() {
        when(courses.selectById(1L)).thenReturn(course(1L));
        var self = point(1L, 1L, "需求分析", "[1,4]");
        var cycleA = point(2L, 1L, "系统设计", "[3]");
        var cycleB = point(3L, 1L, "实现", "[2]");
        var otherCourse = point(4L, 2L, "其他课程知识", "[]");
        when(points.selectList(any())).thenReturn(List.of(self, cycleA, cycleB, otherCourse));

        var report = service().quality(1L);

        assertThat(report.issues()).extracting(PrerequisiteQualityService.Issue::code)
                .contains("SELF_REFERENCE", "CROSS_COURSE_REFERENCE", "CYCLE");
        assertThat(report.pointCount()).isEqualTo(3);
    }

    @Test
    void reportsMalformedJsonAndNonArrayShapes() {
        when(courses.selectById(1L)).thenReturn(course(1L));
        when(points.selectList(any())).thenReturn(List.of(
                point(1L, 1L, "需求分析", "not-json"),
                point(2L, 1L, "系统设计", "{}"),
                point(3L, 1L, "实现", "[1.5,0]")
        ));

        var report = service().quality(1L);

        assertThat(report.issues()).extracting(PrerequisiteQualityService.Issue::code)
                .contains("INVALID_JSON", "INVALID_SHAPE", "INVALID_ID");
    }

    private PrerequisiteQualityService service() {
        return new PrerequisiteQualityService(courses, points, json);
    }

    private CourseEntity course(Long id) {
        var value = new CourseEntity();
        value.setId(id);
        value.setEnabled(true);
        return value;
    }

    private KnowledgePointEntity point(Long id, Long courseId, String name, String prerequisites) {
        var value = new KnowledgePointEntity();
        value.setId(id);
        value.setCourseId(courseId);
        value.setName(name);
        value.setEnabled(true);
        value.setPrerequisitesJson(prerequisites);
        return value;
    }
}
