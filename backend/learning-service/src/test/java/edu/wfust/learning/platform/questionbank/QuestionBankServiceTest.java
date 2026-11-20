package edu.wfust.learning.platform.questionbank;

import edu.wfust.learning.platform.admin.AdminPayloads;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.entity.QuestionBankVersionEntity;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.QuestionBankImportMapper;
import edu.wfust.learning.platform.mapper.QuestionBankVersionMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionBankServiceTest {
    private final CourseMapper courses = mock(CourseMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final QuizQuestionMapper questions = mock(QuizQuestionMapper.class);
    private final QuestionBankVersionMapper versions = mock(QuestionBankVersionMapper.class);
    private final QuestionBankImportMapper imports = mock(QuestionBankImportMapper.class);
    private final QuestionBankService service = new QuestionBankService(
        courses, points, questions, versions, imports, JsonMapper.builder().build(),
        Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void importKeepsValidRowsAndRecordsInvalidRowsWithoutEnablingQuestions() {
        var version = new QuestionBankVersionEntity();
        version.setId(11L);
        version.setCourseId(1L);
        version.setStatus("DRAFT");
        when(versions.selectById(11L)).thenReturn(version);

        var course = new CourseEntity();
        course.setId(1L);
        course.setEnabled(true);
        when(courses.selectById(1L)).thenReturn(course);
        var point = new KnowledgePointEntity();
        point.setId(2L);
        point.setCourseId(1L);
        point.setEnabled(true);
        when(points.selectById(2L)).thenReturn(point);

        var result = service.importQuestions(11L, 7L, new QuestionBankService.ImportRequest(List.of(
            validRow(),
            new QuestionBankService.ImportRow(1L, 999L, "SINGLE", "invalid point", List.of(
                new AdminPayloads.Option("A", "yes"), new AdminPayloads.Option("B", "no")),
                List.of("A"), "explanation", 1)
        )));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(2);
            assertThat(error.message()).isNotBlank();
        });
        var inserted = org.mockito.ArgumentCaptor.forClass(QuizQuestionEntity.class);
        verify(questions).insert(inserted.capture());
        assertThat(inserted.getValue().getEnabled()).isFalse();
        assertThat(inserted.getValue().getVersionId()).isEqualTo(11L);
        verify(imports).insert(any(edu.wfust.learning.platform.entity.QuestionBankImportEntity.class));
    }

    @Test
    void publishArchivesOtherPublishedVersionsAndEnablesCurrentVersionQuestions() {
        var current = new QuestionBankVersionEntity();
        current.setId(11L);
        current.setCourseId(1L);
        current.setStatus("DRAFT");
        when(versions.selectById(11L)).thenReturn(current);
        when(versions.countQuestions(11L)).thenReturn(3L);

        var published = new QuestionBankVersionEntity();
        published.setId(10L);
        published.setCourseId(1L);
        published.setStatus("PUBLISHED");
        when(versions.findPublishedByCourse(1L, 11L)).thenReturn(List.of(published));

        var result = service.publishVersion(11L);

        assertThat(result.status()).isEqualTo("PUBLISHED");
        var order = inOrder(versions, questions);
        order.verify(versions).findPublishedByCourse(1L, 11L);
        order.verify(versions).archivePublished(1L, 11L);
        order.verify(questions).disableVersionQuestions(10L);
        order.verify(questions).enableVersionQuestions(11L);
        order.verify(versions).markPublished(eq(11L), any());
    }

    private QuestionBankService.ImportRow validRow() {
        return new QuestionBankService.ImportRow(1L, 2L, "SINGLE", "valid question", List.of(
            new AdminPayloads.Option("A", "yes"), new AdminPayloads.Option("B", "no")),
            List.of("A"), "because this is valid", 2);
    }
}
