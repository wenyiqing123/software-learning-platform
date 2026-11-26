package edu.wfust.learning.platform.microcourse;

import edu.wfust.learning.platform.course.content.CourseStudyMapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.entity.MicroLessonDraftEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.MicroLessonMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import edu.wfust.learning.platform.knowledge.PythonGraphClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MicroLessonServiceTest {
    private final CourseMapper courses = mock(CourseMapper.class);
    private final CourseStudyMapper study = mock(CourseStudyMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final QuizQuestionMapper questions = mock(QuizQuestionMapper.class);
    private final MicroLessonMapper drafts = mock(MicroLessonMapper.class);
    private final PythonGraphClient graph = mock(PythonGraphClient.class);
    private final AiMicroLessonClient ai = mock(AiMicroLessonClient.class);
    private final MicroLessonService service = new MicroLessonService(courses, study, points, questions, drafts, graph, ai,
        JsonMapper.builder().build(), Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void generatesDraftOnlyWhenRealGraphEvidenceExists() {
        var course = new CourseEntity(); course.setId(1L); course.setEnabled(true);
        when(courses.selectById(1L)).thenReturn(course);
        when(study.section(1L, 21L)).thenReturn(new CourseStudyMapper.SectionRow(21L, 1L, 2L, 31L,
            "理解数据流图", "[\"识别加工和数据流\"]", "[{\"heading\":\"要点\",\"paragraphs\":[\"加工负责变换数据。\"]}]", "[]", 1,
            "数据流图 DFD", "数据流图DFD"));
        var point = new KnowledgePointEntity(); point.setId(31L); point.setCourseId(1L); point.setEnabled(true); point.setPrerequisitesJson("[]");
        when(points.selectById(31L)).thenReturn(point);
        when(drafts.commonErrors(31L)).thenReturn(List.of());
        when(graph.retrieve("数据流图DFD", 12)).thenReturn(new PythonGraphClient.PythonGraphResponse("数据流图DFD", List.of(),
            List.of(new PythonGraphClient.Triple("加工", "命名", "动宾短语", 0.9)), "prompt", true, "template"));
        when(ai.generate(any(), any())).thenReturn(new AiMicroLessonClient.GenerationResult("只根据课程证据生成的微课摘要。"));

        var result = service.generate(7L, "Bearer token", new MicroLessonService.GenerateRequest(1L, 21L));

        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.sectionId()).isEqualTo(21L);
        var inserted = org.mockito.ArgumentCaptor.forClass(MicroLessonDraftEntity.class);
        verify(drafts).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getValue().getEvidenceJson()).contains("数据流图DFD");
        assertThat(inserted.getValue().getGeneratedText()).contains("微课摘要");
    }

    @Test
    void refusesGenerationWithoutGraphEvidence() {
        var course = new CourseEntity(); course.setId(1L); course.setEnabled(true);
        when(courses.selectById(1L)).thenReturn(course);
        when(study.section(1L, 21L)).thenReturn(new CourseStudyMapper.SectionRow(21L, 1L, 2L, 31L,
            "理解数据流图", "[]", "[]", "[]", 1, "数据流图 DFD", "数据流图DFD"));
        var point = new KnowledgePointEntity(); point.setId(31L); point.setCourseId(1L); point.setEnabled(true); point.setPrerequisitesJson("[]");
        when(points.selectById(31L)).thenReturn(point);
        when(graph.retrieve("数据流图DFD", 12)).thenReturn(new PythonGraphClient.PythonGraphResponse("数据流图DFD", List.of(), List.of(), "", false, "template"));

        assertThatThrownBy(() -> service.generate(7L, "Bearer token", new MicroLessonService.GenerateRequest(1L, 21L)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("证据");
        verifyNoInteractions(ai);
        verify(drafts, never()).insert(any(MicroLessonDraftEntity.class));
    }

    @Test
    void publishingArchivesPreviousSectionDraftAndPublishesCurrent() {
        var current = new MicroLessonDraftEntity(); current.setId(11L); current.setCourseId(1L); current.setSectionId(21L); current.setStatus("DRAFT");
        when(drafts.selectById(11L)).thenReturn(current);
        var previous = new MicroLessonDraftEntity(); previous.setId(10L); previous.setCourseId(1L); previous.setSectionId(21L); previous.setStatus("PUBLISHED");
        when(drafts.findPublished(1L, 21L, 11L)).thenReturn(List.of(previous));

        var result = service.publish(11L);

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(drafts).archivePublished(1L, 21L, 11L);
        verify(drafts).markPublished(eq(11L), any());
    }

    @Test
    void listsAdminSectionsAndUpdatesDraftContentOnlyWhenDraft() {
        var course = new CourseEntity(); course.setId(1L); course.setEnabled(false);
        when(courses.selectById(1L)).thenReturn(course);
        when(study.chapters(1L)).thenReturn(List.of(new CourseStudyMapper.ChapterRow(2L, 1L, "需求工程", "说明", 1)));
        when(study.sections(1L)).thenReturn(List.of(new CourseStudyMapper.SectionRow(21L, 1L, 2L, 31L,
            "理解数据流图", "[]", "[]", "[]", 1, "数据流图 DFD", "数据流图DFD")));

        var sections = service.sections(1L);
        assertThat(sections).singleElement().satisfies(s -> {
            assertThat(s.chapterName()).isEqualTo("需求工程");
            assertThat(s.title()).isEqualTo("理解数据流图");
        });

        var row = new MicroLessonDraftEntity();
        row.setId(11L); row.setCourseId(1L); row.setSectionId(21L); row.setStatus("DRAFT");
        row.setTitle("旧标题"); row.setGeneratedText("旧正文");
        when(drafts.selectById(11L)).thenReturn(row);

        var updated = service.updateDraft(11L, new MicroLessonService.UpdateDraftRequest("新标题", "更新后的正文"));
        assertThat(updated.title()).isEqualTo("新标题");
        assertThat(updated.generatedText()).isEqualTo("更新后的正文");
        verify(drafts).updateById(row);

        row.setStatus("PUBLISHED");
        assertThatThrownBy(() -> service.updateDraft(11L, new MicroLessonService.UpdateDraftRequest("标题", "正文")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
