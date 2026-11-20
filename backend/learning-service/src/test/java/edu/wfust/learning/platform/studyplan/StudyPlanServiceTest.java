package edu.wfust.learning.platform.studyplan;

import edu.wfust.learning.platform.mapper.StudyPlanMapper;
import edu.wfust.learning.platform.mapper.SpacedReviewMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudyPlanServiceTest {
    private final StudyPlanMapper plans=mock(StudyPlanMapper.class);
    private final SpacedReviewMapper reviews=mock(SpacedReviewMapper.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-19T02:00:00Z"),ZoneId.of("Asia/Shanghai"));
    private final StudyPlanService service=new StudyPlanService(plans,reviews,clock);

    @Test void rejectsBudgetOutsideFiveToTwoHundredFortyMinutes() {
        assertThatThrownBy(()->service.generate(7,4)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.generate(7,241)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(plans,reviews);
    }

    @Test void generatesDeterministicRealTasksWithinBudget() {
        var date=clock.instant().atZone(clock.getZone()).toLocalDate();
        when(plans.findPlan(eq(7L),any())).thenReturn(new StudyPlanMapper.PlanRow(11L,7L,date,20));
        when(plans.tasks(7L,11L)).thenReturn(List.of()).thenAnswer(invocation->inserted.stream().map(t->new StudyPlanMapper.TaskRow((long)inserted.indexOf(t)+1,11L,7L,t.type(),t.targetId(),t.title(),t.detail(),t.minutes(),t.reason(),t.state(),t.sortOrder(),t.courseId())).toList());
        when(plans.completedMinutes(7L,11L)).thenReturn(0);
        var review=new StudyPlanMapper.ReviewCandidate();review.setTargetId(21L);review.setTitle("复习DFD");review.setDetail("数据流图");review.setCourseId(1L);
        when(plans.reviewCandidates(eq(7L),any())).thenReturn(List.of(review));
        var readingOne=new StudyPlanMapper.ReadingCandidate();readingOne.setTargetId(101L);readingOne.setTitle("阅读需求分析");readingOne.setDetail("软件工程 · 第1章");readingOne.setCourseId(1L);
        var readingTwo=new StudyPlanMapper.ReadingCandidate();readingTwo.setTargetId(102L);readingTwo.setTitle("阅读数据流图");readingTwo.setDetail("软件工程 · 第1章");readingTwo.setCourseId(1L);
        when(plans.readingCandidates(7L)).thenReturn(List.of(readingOne,readingTwo));
        var practice=new StudyPlanMapper.PracticeCandidate();practice.setTargetId(31L);practice.setTitle("练习DFD");practice.setDetail("数据流图");practice.setCourseId(1L);practice.setMastery(0.0);
        when(plans.practiceCandidates(7L)).thenReturn(List.of(practice));
        when(plans.insertTask(any())).thenAnswer(inv->{inserted.add(inv.getArgument(0));return 1;});
        var result=service.generate(7,20);
        assertThat(result.tasks()).extracting(StudyPlanService.PlanTaskView::type).containsExactly("REVIEW","READING");
        assertThat(result.tasks()).extracting(StudyPlanService.PlanTaskView::minutes).containsExactly(5,15);
        assertThat(result.tasks().stream().mapToInt(StudyPlanService.PlanTaskView::minutes).sum()).isLessThanOrEqualTo(result.budgetMinutes());
        assertThat(result.tasks().get(0).path()).isEqualTo("/review?wrongQuestionId=21");
        assertThat(result.tasks().get(1).path()).contains("courseId=1").contains("sectionId=101");
    }
    private final List<StudyPlanMapper.TaskRow> inserted=new ArrayList<>();
    @Test void onlyCompletesPlanTaskAfterLearningEvidenceExists() {
        var date=clock.instant().atZone(clock.getZone()).toLocalDate();
        var plan=new StudyPlanMapper.PlanRow(11L,7L,date,30);
        var pending=new StudyPlanMapper.TaskRow(4L,11L,7L,"PRACTICE",31L,"练习DFD","数据流图",10,"掌握度较低","PENDING",1,1L);
        when(plans.findPlan(7L,date)).thenReturn(plan);
        when(plans.findTask(7L,11L,4L)).thenReturn(pending);
        when(plans.hasCorrectAttempt(eq(7L),eq(31L),any(),any())).thenReturn(0);
        assertThatThrownBy(()->service.updateTask(7,4,"COMPLETE")).isInstanceOf(ResponseStatusException.class);
        verify(plans,never()).updateState(anyLong(),anyLong(),anyLong(),anyString(),any());
        verify(plans).hasCorrectAttempt(eq(7L),eq(31L),any(),any());
    }}
