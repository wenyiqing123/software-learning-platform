package edu.wfust.learning.platform.quiz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import edu.wfust.learning.platform.entity.WrongQuestionEntity;
import edu.wfust.learning.platform.mapper.SpacedReviewMapper;
import edu.wfust.learning.platform.mapper.WrongQuestionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpacedReviewService {
    private final SpacedReviewMapper reviews;
    private final QuizService quizzes;
    private final WrongQuestionMapper wrongQuestions;
    private final ObjectMapper json;
    private final Clock clock;
    private final SpacedReviewPolicy policy=new SpacedReviewPolicy();
    public SpacedReviewService(SpacedReviewMapper reviews,QuizService quizzes,WrongQuestionMapper wrongQuestions,ObjectMapper json,Clock clock){this.reviews=reviews;this.quizzes=quizzes;this.wrongQuestions=wrongQuestions;this.json=json;this.clock=clock;}

    @Transactional
    public List<ReviewCard> due(long userId,Long targetWrongId){
        LocalDateTime now=LocalDateTime.now(clock);synchronize(userId);
        List<SpacedReviewMapper.ReviewCandidate> rows=reviews.due(userId,now);
        if(targetWrongId!=null){
            rows=rows.stream().filter(row->targetWrongId.equals(row.wrongQuestionId())).toList();
            if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"该错题未到复习时间或不存在");
        }
        return rows.stream().map(this::toCard).toList();
    }

    @Transactional
    public List<ReviewCard> paused(long userId){
        synchronize(userId);
        return reviews.paused(userId).stream().map(this::toCard).toList();
    }

    private ReviewCard toCard(SpacedReviewMapper.ReviewCandidate row){
        return new ReviewCard(row.wrongQuestionId(),row.questionId(),row.question(),row.knowledgePoint(),row.questionType(),readOptions(row.optionsJson()),row.wrongCount(),row.dueAt(),row.intervalMinutes(),row.sectionId());
    }

    @Transactional
    public ReviewResult submit(long userId,long wrongId,ReviewRequest request){
        LocalDateTime now=LocalDateTime.now(clock);
        WrongQuestionEntity wrong=wrongQuestions.selectOne(new QueryWrapper<WrongQuestionEntity>().eq("id",wrongId).eq("user_id",userId).last("LIMIT 1 FOR UPDATE"));
        if(wrong==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"错题不存在");
        Map<String,Object> prior=reviews.findRequest(userId,request.requestKey().toString());
        if(prior!=null){
            if(((Number)prior.get("wrong_question_id")).longValue()!=wrongId||!requestJson(request).equals(prior.get("request_json"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"复习请求编号已用于其他提交");
            return readResult((String)prior.get("response_json"));
        }
        if("MASTERED".equals(wrong.getStatus())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"该错题已掌握，请从错题列表重新开启");
        synchronize(userId);
        SpacedReviewMapper.ReviewSchedule schedule=reviews.findOwnedForUpdate(userId,wrongId);
        if(schedule==null||!"ACTIVE".equals(schedule.state())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"复习任务不存在或已暂停");
        if(schedule.dueAt().isAfter(now)) throw new ResponseStatusException(HttpStatus.CONFLICT,"该题尚未到复习时间");
        var answer=quizzes.submitForSpacedReview(userId,wrong.getQuestionId(),request.answer());
        int interval=policy.nextIntervalMinutes(request.recallRating(),answer.correct());
        LocalDateTime next=now.plusMinutes(interval);
        int streak=answer.correct()&&request.recallRating()>=2?(wrong.getReviewStreak()==null?0:wrong.getReviewStreak())+1:0;
        boolean mastered=streak>=3;
        reviews.updateAfterReview(userId,wrongId,mastered?"MASTERED":"ACTIVE",interval,next,now,answer.attemptId());
        wrongQuestions.update(null,new UpdateWrapper<WrongQuestionEntity>().eq("id",wrongId).eq("user_id",userId).set("review_streak",streak).set("status",mastered?"MASTERED":"UNMASTERED").set("mastered_at",mastered?now:null));
        ReviewResult result=new ReviewResult(answer.attemptId(),answer.questionId(),answer.correct(),answer.correctAnswer(),answer.explanation(),request.recallRating(),interval,next,mastered,streak);
        reviews.insertLog(userId,wrongId,request.requestKey().toString(),answer.attemptId(),request.recallRating(),answer.correct(),interval,now,next,writeResult(result),requestJson(request));
        return result;
    }

    @Transactional
    public void setState(long userId,long wrongId,String state){
        if(!List.of("ACTIVE","PAUSED","MASTERED").contains(state)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效的复习状态");
        WrongQuestionEntity wrong=wrongQuestions.selectOne(new QueryWrapper<WrongQuestionEntity>().eq("id",wrongId).eq("user_id",userId).last("LIMIT 1"));
        if(wrong==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"错题不存在");
        if(!"MASTERED".equals(state)) wrongQuestions.update(null,new UpdateWrapper<WrongQuestionEntity>().eq("id",wrongId).eq("user_id",userId).set("status","UNMASTERED").set("mastered_at",null));
        synchronize(userId);
        if(reviews.setState(userId,wrongId,state,LocalDateTime.now(clock))==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"复习任务不存在");
        if("MASTERED".equals(state)) wrongQuestions.update(null,new UpdateWrapper<WrongQuestionEntity>().eq("id",wrongId).eq("user_id",userId).set("status","MASTERED").setSql("mastered_at=CURRENT_TIMESTAMP(6)"));
    }
    private void synchronize(long userId){reviews.synchronizeOpenWrongQuestions(userId);reviews.reopenNewlyUnmastered(userId);reviews.resetAfterNewAttempt(userId);}
    private String requestJson(ReviewRequest request){try{return json.writeValueAsString(new RequestPayload(request.answer(),request.recallRating()));}catch(Exception e){throw new IllegalStateException("无法校验复习请求",e);}}
    private List<QuizService.OptionView> readOptions(String raw){try{return json.readValue(raw,new tools.jackson.core.type.TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("题目选项数据格式错误",e);}}
    private String writeResult(ReviewResult result){try{return json.writeValueAsString(result);}catch(Exception e){throw new IllegalStateException("无法保存复习结果",e);}}
    private ReviewResult readResult(String raw){try{return json.readValue(raw,ReviewResult.class);}catch(Exception e){throw new IllegalStateException("复习结果记录格式错误",e);}}
    private record RequestPayload(List<String> answer,int recallRating) { }
    public record ReviewRequest(List<String> answer,int recallRating,UUID requestKey) { }
    public record ReviewCard(Long wrongQuestionId,Long questionId,String question,String knowledgePoint,String questionType,List<QuizService.OptionView> options,Integer wrongCount,LocalDateTime dueAt,Integer previousIntervalMinutes,Long sectionId) { }
    public record ReviewResult(Long attemptId,Long questionId,boolean correct,List<String> correctAnswer,String explanation,int recallRating,int intervalMinutes,LocalDateTime nextDueAt,boolean mastered,int consecutiveSuccessfulReviews) { }
}
