package edu.wfust.learning.platform.quiz;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.mapper.*;
import edu.wfust.learning.platform.messaging.LearningEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class QuizServiceValidationTest {
    @Test void questionListProjectionNeverExposesTheStandardAnswer() {
        var questions = mock(QuizQuestionMapper.class);
        var question = new QuizQuestionEntity();
        question.setId(1L); question.setEnabled(true); question.setQuestionType("SINGLE");
        question.setStem("加工如何命名？");
        question.setOptionsJson("[{\"key\":\"A\",\"text\":\"动宾短语\"}]");
        question.setAnswerJson("[\"A\"]");
        when(questions.selectList(any())).thenReturn(List.of(question));
        var service = new QuizService(questions, mock(QuizAttemptMapper.class), mock(WrongQuestionMapper.class),
                mock(KnowledgePointMapper.class), new ObjectMapper(), mock(LearningEventPublisher.class));

        var tree = new ObjectMapper().valueToTree(service.questions(null));
        assertThat(tree.get(0).has("answer")).isFalse();
        assertThat(tree.get(0).has("answerJson")).isFalse();
        assertThat(tree.get(0).path("options").get(0).path("text").asText()).isEqualTo("动宾短语");
    }

    @Test void wrongBookMutationRequiresTheAuthenticatedOwner() {
        var wrong = mock(WrongQuestionMapper.class);
        when(wrong.selectOne(any())).thenReturn(null);
        var service = new QuizService(mock(QuizQuestionMapper.class), mock(QuizAttemptMapper.class), wrong,
                mock(KnowledgePointMapper.class), new ObjectMapper(), mock(LearningEventPublisher.class));

        assertThatThrownBy(() -> service.markMastered(2L, 9L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(404));
        verify(wrong, never()).update(any(), any());
    }

    @Test void invalidOptionDoesNotCreateAttemptOrWrongQuestion(){
        var questions=mock(QuizQuestionMapper.class);var attempts=mock(QuizAttemptMapper.class);var wrong=mock(WrongQuestionMapper.class);var publisher=mock(LearningEventPublisher.class);
        var q=new QuizQuestionEntity();q.setId(1L);q.setEnabled(true);q.setQuestionType("SINGLE");q.setOptionsJson("[{\"key\":\"A\",\"text\":\"是\"},{\"key\":\"B\",\"text\":\"否\"}]");q.setAnswerJson("[\"A\"]");
        when(questions.selectOne(any())).thenReturn(q);
        var service=new QuizService(questions,attempts,wrong,mock(KnowledgePointMapper.class),new ObjectMapper(),publisher);
        assertThatThrownBy(()->service.submit(9L,1L,List.of("NONEXISTENT"))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(attempts,wrong,publisher);
    }
}
