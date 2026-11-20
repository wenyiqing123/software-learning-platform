package edu.wfust.learning.platform.quiz;

import edu.wfust.learning.platform.entity.WrongQuestionEntity;
import edu.wfust.learning.platform.mapper.SpacedReviewMapper;
import edu.wfust.learning.platform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpacedReviewServiceTest {
    private final SpacedReviewMapper reviews=mock(SpacedReviewMapper.class);
    private final QuizService quizzes=mock(QuizService.class);
    private final WrongQuestionMapper wrong=mock(WrongQuestionMapper.class);
    private final SpacedReviewService service=new SpacedReviewService(reviews,quizzes,wrong,JsonMapper.builder().build(),Clock.fixed(Instant.parse("2026-11-19T02:00:00Z"),ZoneId.of("Asia/Shanghai")));

    @Test void wrongAnswerAlwaysSchedulesShortRetryWithoutChangingSelfRating() {
        WrongQuestionEntity entity=new WrongQuestionEntity();entity.setId(9L);entity.setUserId(7L);entity.setQuestionId(31L);entity.setStatus("UNMASTERED");entity.setReviewStreak(2);
        when(wrong.selectOne(any())).thenReturn(entity);when(reviews.findRequest(eq(7L),anyString())).thenReturn(null);
        when(reviews.findOwnedForUpdate(7L,9L)).thenReturn(new SpacedReviewMapper.ReviewSchedule(1L,7L,9L,"ACTIVE",0,LocalDateTime.now(Clock.fixed(Instant.parse("2026-11-19T02:00:00Z"),ZoneId.of("Asia/Shanghai"))).minusMinutes(1),null));
        when(quizzes.submitForSpacedReview(7L,31L,List.of("B"))).thenReturn(new QuizService.SubmitAnswerResponse(44L,31L,false,List.of("A"),"解析",1));
        var result=service.submit(7,9,new SpacedReviewService.ReviewRequest(List.of("B"),3,UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertThat(result.correct()).isFalse();assertThat(result.recallRating()).isEqualTo(3);assertThat(result.intervalMinutes()).isEqualTo(10);assertThat(result.mastered()).isFalse();
        verify(reviews).updateAfterReview(eq(7L),eq(9L),eq("ACTIVE"),eq(10),any(),any(),eq(44L));
        verify(reviews).insertLog(eq(7L),eq(9L),anyString(),eq(44L),eq(3),eq(false),eq(10),any(),any(),anyString(),anyString());
    }

    @Test void replaysSameRequestWithoutSubmittingAnotherAttempt() throws Exception {
        var expected=new SpacedReviewService.ReviewResult(44L,31L,true,List.of("A"),"解析",2,4_320,LocalDateTime.of(2026,11,22,10,0),false,1);
        String response=new String(JsonMapper.builder().build().writeValueAsBytes(expected),java.nio.charset.StandardCharsets.UTF_8);
        when(wrong.selectOne(any())).thenReturn(new WrongQuestionEntity());
        when(reviews.findRequest(7L,"00000000-0000-0000-0000-000000000002")).thenReturn(Map.of("wrong_question_id",9L,"request_json","{\"answer\":[\"A\"],\"recallRating\":2}","response_json",response));
        var actual=service.submit(7,9,new SpacedReviewService.ReviewRequest(List.of("A"),2,UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertThat(actual).isEqualTo(expected);verifyNoInteractions(quizzes);
    }

    @Test void doesNotAllowCrossUserReviewAccess() {
        when(wrong.selectOne(any())).thenReturn(null);
        assertThatThrownBy(()->service.submit(8,9,new SpacedReviewService.ReviewRequest(List.of("A"),2,UUID.randomUUID()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(reviews,quizzes);
    }

    @Test void dueCanOpenOneSpecifiedWrongQuestionForAStudyPlanLink() {
        when(reviews.due(eq(7L),any())).thenReturn(List.of(
                new SpacedReviewMapper.ReviewCandidate(1L,9L,LocalDateTime.now(),0,2,31L,"定点复习","数据流图","SINGLE","[]",4L,1L),
                new SpacedReviewMapper.ReviewCandidate(2L,10L,LocalDateTime.now(),0,1,32L,"其他复习","需求","SINGLE","[]",5L,1L)));
        var result=service.due(7,9L);
        assertThat(result).singleElement().satisfies(card -> assertThat(card.wrongQuestionId()).isEqualTo(9L));
    }

    @Test void missingOrNotDueTargetReturnsNotFound() {
        when(reviews.due(eq(7L),any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.due(7,9L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
