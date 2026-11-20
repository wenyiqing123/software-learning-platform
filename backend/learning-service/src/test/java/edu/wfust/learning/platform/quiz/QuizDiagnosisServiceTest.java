package edu.wfust.learning.platform.quiz;

import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.QuizDiagnosisMapper;
import edu.wfust.learning.platform.mapper.QuizOptionDiagnosisMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuizDiagnosisServiceTest {
    private final QuizQuestionMapper questions = mock(QuizQuestionMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final QuizOptionDiagnosisMapper diagnoses = mock(QuizOptionDiagnosisMapper.class);
    private final QuizDiagnosisMapper query = mock(QuizDiagnosisMapper.class);
    private final QuizDiagnosisService service = new QuizDiagnosisService(questions, points, diagnoses, query, JsonMapper.builder().build());

    @Test
    void returnsOnlyConfiguredSelectedDistractorsWithCourseEvidence() throws Exception {
        QuizQuestionEntity question = question(41L, 1L, "[{\"key\":\"A\",\"text\":\"正确\"},{\"key\":\"B\",\"text\":\"混淆选项\"}]", "[\"A\"]");
        when(questions.selectById(41L)).thenReturn(question);
        var row = new QuizDiagnosisMapper.DiagnosisRow();
        row.setOptionKey("B"); row.setKnowledgePointId(7L); row.setKnowledgePointName("数据流图");
        row.setRationale("该干扰项把数据流与加工概念混淆"); row.setSectionId(101L); row.setSectionTitle("理解数据流图"); row.setPracticeQuestionId(42L);
        when(query.matching(eq(41L), eq(List.of("B")))).thenReturn(List.of(row));

        var result = service.diagnose(41L, List.of("A", "B"));

        assertThat(result).containsExactly(new QuizDiagnosisService.DiagnosisView("B", "混淆选项", 7L, "数据流图",
            "该干扰项把数据流与加工概念混淆", 101L, "理解数据流图", 42L));
        verify(query).matching(41L, List.of("B"));
    }

    @Test
    void doesNotDiagnoseWhenLearnerSelectedOnlyCorrectAnswer() {
        when(questions.selectById(41L)).thenReturn(question(41L, 1L, "[{\"key\":\"A\",\"text\":\"正确\"}]", "[\"A\"]"));
        assertThat(service.diagnose(41L, List.of("A"))).isEmpty();
        verifyNoInteractions(query);
    }

    @Test
    void rejectsConfigurationForCorrectOption() {
        when(questions.selectById(41L)).thenReturn(question(41L, 1L, "[{\"key\":\"A\",\"text\":\"正确\"},{\"key\":\"B\",\"text\":\"错误\"}]", "[\"A\"]"));
        var point = new KnowledgePointEntity(); point.setId(7L); point.setCourseId(1L); point.setEnabled(true); when(points.selectById(7L)).thenReturn(point);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.replace(41L, List.of(new QuizDiagnosisService.ConfigEntry("A", 7L, "依据"))))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("正确答案");
        verify(diagnoses, never()).delete(any());
    }

    private QuizQuestionEntity question(Long id, Long courseId, String options, String answers) {
        var row = new QuizQuestionEntity(); row.setId(id); row.setCourseId(courseId); row.setEnabled(true); row.setOptionsJson(options); row.setAnswerJson(answers); return row;
    }
}
