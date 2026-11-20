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
    @Test void invalidOptionDoesNotCreateAttemptOrWrongQuestion(){
        var questions=mock(QuizQuestionMapper.class);var attempts=mock(QuizAttemptMapper.class);var wrong=mock(WrongQuestionMapper.class);var publisher=mock(LearningEventPublisher.class);
        var q=new QuizQuestionEntity();q.setId(1L);q.setEnabled(true);q.setQuestionType("SINGLE");q.setOptionsJson("[{\"key\":\"A\",\"text\":\"是\"},{\"key\":\"B\",\"text\":\"否\"}]");q.setAnswerJson("[\"A\"]");
        when(questions.selectOne(any())).thenReturn(q);
        var service=new QuizService(questions,attempts,wrong,mock(KnowledgePointMapper.class),new ObjectMapper(),publisher);
        assertThatThrownBy(()->service.submit(9L,1L,List.of("NONEXISTENT"))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(attempts,wrong,publisher);
    }
}
