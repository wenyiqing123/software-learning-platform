package edu.wfust.learning.platform.studyplan;

import edu.wfust.learning.platform.mapper.StudyPlanMapper;
import edu.wfust.learning.platform.mapper.SpacedReviewMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudyPlanService {
    private static final int REVIEW_MINUTES=5, READ_MINUTES=15, PRACTICE_MINUTES=10;
    private final StudyPlanMapper plans;
    private final SpacedReviewMapper reviews;
    private final Clock clock;

    public StudyPlanService(StudyPlanMapper plans,SpacedReviewMapper reviews,Clock clock) {
        this.plans=plans;this.reviews=reviews;this.clock=clock;
    }

    @Transactional
    public PlanView today(long userId) {
        LocalDate date=LocalDate.now(clock);
        StudyPlanMapper.PlanRow existing=plans.findPlan(userId,date);
        return generate(userId,existing==null?30:existing.budgetMinutes());
    }

    @Transactional
    public PlanView generate(long userId,int budget) {
        if(budget<5 || budget>240) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"每日学习时长需在 5–240 分钟之间");
        LocalDate date=LocalDate.now(clock);
        StudyPlanMapper.PlanRow plan=new StudyPlanMapper.PlanRow(null,userId,date,budget);
        plans.savePlan(plan);
        plan=plans.findPlan(userId,date);
        if(plan==null) throw new IllegalStateException("无法保存每日学习计划");
        List<StudyPlanMapper.TaskRow> previous=plans.tasks(userId,plan.id());
        Map<String,StudyPlanMapper.TaskRow> finalized=new LinkedHashMap<>();
        for(var task:previous) if(!"PENDING".equals(task.state())) finalized.put(key(task.type(),task.targetId()),task);
        plans.deletePending(userId,plan.id());
        int completedMinutes=plans.completedMinutes(userId,plan.id());
        if(completedMinutes>budget) throw new ResponseStatusException(HttpStatus.CONFLICT,"已完成任务预计用时超过新预算，请提高预算后刷新");
        int remaining=budget-completedMinutes,order=1;
        LocalDateTime tomorrow=date.plusDays(1).atStartOfDay();
        reviews.synchronizeOpenWrongQuestions(userId);
        reviews.reopenNewlyUnmastered(userId);
        reviews.resetAfterNewAttempt(userId);
        for(var candidate:plans.reviewCandidates(userId,tomorrow))
            remaining=add(plan,userId,"REVIEW",candidate.getTargetId(),candidate.getTitle(),candidate.getDetail(),REVIEW_MINUTES,"已到复习时间",remaining,order++,finalized,candidate.getCourseId());
        for(var candidate:plans.readingCandidates(userId))
            remaining=add(plan,userId,"READING",candidate.getTargetId(),candidate.getTitle(),candidate.getDetail(),READ_MINUTES,"课程小节尚未标记完成",remaining,order++,finalized,candidate.getCourseId());
        for(var candidate:plans.practiceCandidates(userId))
            remaining=add(plan,userId,"PRACTICE",candidate.getTargetId(),candidate.getTitle(),candidate.getDetail(),PRACTICE_MINUTES,"相关知识点掌握记录低于 60",remaining,order++,finalized,candidate.getCourseId());
        return view(plan,plans.tasks(userId,plan.id()));
    }

    private int add(StudyPlanMapper.PlanRow plan,long userId,String type,long targetId,String title,String detail,int minutes,
                    String reason,int remaining,int order,Map<String,StudyPlanMapper.TaskRow> finalized,Long courseId) {
        if(finalized.containsKey(key(type,targetId)) || remaining<minutes) return remaining;
        plans.insertTask(new StudyPlanMapper.TaskRow(null,plan.id(),userId,type,targetId,title,detail,minutes,reason,"PENDING",order,courseId));
        return remaining-minutes;
    }

    @Transactional
    public PlanTaskView updateTask(long userId,long taskId,String action) {
        if(!List.of("COMPLETE","SKIP").contains(action)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"任务操作无效");
        LocalDate date=LocalDate.now(clock);
        StudyPlanMapper.PlanRow plan=plans.findPlan(userId,date);
        StudyPlanMapper.TaskRow task=plan==null?null:plans.findTask(userId,plan.id(),taskId);
        if(task==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"学习任务不存在");
        String next="COMPLETE".equals(action)?"COMPLETED":"SKIPPED";
        if("PENDING".equals(task.state()) && "COMPLETED".equals(next)) validateEvidence(userId,task,date);
        if("PENDING".equals(task.state())) plans.updateState(userId,plan.id(),taskId,next,"COMPLETED".equals(next)?LocalDateTime.now(clock):null);
        return taskView(plans.findTask(userId,plan.id(),taskId));
    }

    private void validateEvidence(long userId,StudyPlanMapper.TaskRow task,LocalDate date) {
        LocalDateTime start=date.atStartOfDay(),end=date.plusDays(1).atStartOfDay();
        boolean complete=switch(task.type()) {
            case "READING" -> plans.sectionCompleted(userId,task.targetId())>0;
            case "PRACTICE" -> plans.hasCorrectAttempt(userId,task.targetId(),start,end)>0;
            case "REVIEW" -> plans.hasCorrectReview(userId,task.targetId(),start,end)>0;
            default -> false;
        };
        if(!complete) throw new ResponseStatusException(HttpStatus.CONFLICT,"请先完成对应课程阅读、正确作答或复习，再标记计划任务完成");
    }

    private PlanView view(StudyPlanMapper.PlanRow plan,List<StudyPlanMapper.TaskRow> rows) {
        return new PlanView(plan.id(),plan.planDate(),plan.budgetMinutes(),rows.stream().map(this::taskView).toList());
    }
    private PlanTaskView taskView(StudyPlanMapper.TaskRow row) {
        String path=switch(row.type()) {
            case "REVIEW" -> "/review?wrongQuestionId="+row.targetId();
            case "READING" -> "/courses?courseId="+row.courseId()+"&sectionId="+row.targetId();
            case "PRACTICE" -> "/practice?questionId="+row.targetId();
            default -> "/";
        };
        return new PlanTaskView(row.id(),row.type(),row.targetId(),row.title(),row.detail(),row.minutes(),row.reason(),row.state(),path);
    }
    private String key(String type,long id){return type+":"+id;}
    public record PlanView(Long id,LocalDate date,int budgetMinutes,List<PlanTaskView> tasks) { }
    public record PlanTaskView(Long id,String type,Long targetId,String title,String detail,int minutes,String reason,String state,String path) { }
}
