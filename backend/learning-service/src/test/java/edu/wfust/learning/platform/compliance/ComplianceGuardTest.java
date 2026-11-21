package edu.wfust.learning.platform.compliance;

import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.experiment.AiExperimentController;
import edu.wfust.learning.platform.experiment.ExperimentExport;
import edu.wfust.learning.platform.knowledge.KnowledgeController;
import edu.wfust.learning.platform.knowledge.PythonGraphClient;
import edu.wfust.learning.platform.messaging.LearningEventPublisher;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.QuizAttemptMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import edu.wfust.learning.platform.mapper.WrongQuestionMapper;
import edu.wfust.learning.platform.quiz.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Server-side compliance guards for prompt privacy, honest graph fallback and answer privacy. */
class ComplianceGuardTest {
    @Test
    void experimentExportKeepsPromptMetadataButNeverExportsPromptBody() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("mode", "RANKED");
        result.put("answer", "加工命名使用动宾短语");
        result.put("prompt", "server-only-fixed-constraint-body");
        result.put("provenance", Map.of("systemPromptVersion", "ranked-grounded-v3", "systemPromptSha256", "hash"));
        var run = new AiExperimentController.ExperimentView("1", "问题", "2026-11-20T00:00:00",
                List.of(result), Map.of(), Map.of("experimentType", "METHODS", "metricsByMode", Map.of()), null, null);

        var rows = new ExperimentExport().rows(List.of(run));
        String serialized = rows.toString();
        assertThat(rows.getFirst()).doesNotContainKey("prompt");
        assertThat(rows.getFirst()).containsEntry("promptVersion", "ranked-grounded-v3");
        assertThat(serialized).doesNotContain("server-only-fixed-constraint-body");
    }

    @Test
    void graphDependencyFailureIsPropagatedInsteadOfReturningSyntheticNodes() {
        var graph = mock(PythonGraphClient.class);
        when(graph.retrieve("需求分析", 200)).thenThrow(new RestClientException("知识图谱服务暂不可用"));

        assertThatThrownBy(() -> new KnowledgeController(graph).graph("需求分析"))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("知识图谱服务暂不可用");
        verify(graph).retrieve("需求分析", 200);
    }

    @Test
    void quizProjectionDoesNotExposeAnswerBeforeSubmission() {
        var questions = mock(QuizQuestionMapper.class);
        var question = new QuizQuestionEntity();
        question.setId(1L);
        question.setEnabled(true);
        question.setQuestionType("SINGLE");
        question.setStem("加工如何命名？");
        question.setOptionsJson("[{\"key\":\"A\",\"text\":\"动宾短语\"}]");
        question.setAnswerJson("[\"A\"]");
        when(questions.selectList(any())).thenReturn(List.of(question));
        var service = new QuizService(questions, mock(QuizAttemptMapper.class), mock(WrongQuestionMapper.class),
                mock(KnowledgePointMapper.class), new ObjectMapper(), mock(LearningEventPublisher.class));

        var json = new ObjectMapper().valueToTree(service.questions(null));
        assertThat(json.get(0).has("answer")).isFalse();
        assertThat(json.get(0).has("answerJson")).isFalse();
        assertThat(json.toString()).doesNotContain("correctAnswer");
    }
}
