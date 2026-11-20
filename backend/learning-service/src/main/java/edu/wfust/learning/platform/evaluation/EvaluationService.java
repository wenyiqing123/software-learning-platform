package edu.wfust.learning.platform.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.mapper.EvaluationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EvaluationService {
    private final EvaluationMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;

    public EvaluationService(EvaluationMapper mapper,ObjectMapper json,Clock clock){this.mapper=mapper;this.json=json;this.clock=clock;}

    @Transactional
    public SessionView start(long userId,StartRequest request){
        if(!request.consentAccepted()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"必须先阅读并同意评估说明");
        if(request.conditionKey()==null||request.conditionKey().isBlank()||request.taskKey()==null||request.taskKey().isBlank()||request.consentVersion()==null||request.consentVersion().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"评估条件、任务和同意版本不能为空");
        var row=new EvaluationMapper.SessionRow(null,userId,request.conditionKey().strip(),request.taskKey().strip(),request.consentVersion().strip(),LocalDateTime.now(clock),null,null,null,null,null);
        mapper.insertSession(row); return view(row);
    }

    public List<SessionView> mine(long userId){return mapper.listOwned(userId).stream().map(this::view).toList();}

    @Transactional
    public void measure(long userId,long sessionId,MeasureRequest request){
        owned(userId,sessionId);
        if(!List.of("PRE","POST","EQUIVALENT").contains(request.phase())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"测量阶段必须是 PRE、POST 或 EQUIVALENT");
        if(request.score()<0||request.score()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"测验得分必须在0到100之间");
        mapper.upsertMeasurement(userId,sessionId,request.phase(),request.score(),write(request.responses()),LocalDateTime.now(clock));
    }

    @Transactional
    public SessionView complete(long userId,long sessionId,CompleteRequest request){
        var row=owned(userId,sessionId);
        if(request.elapsedSeconds()<1||request.elapsedSeconds()>86400) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"任务用时必须在1秒到24小时之间");
        if(request.psychologicalEffort()<1||request.psychologicalEffort()>9) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"心理努力评分必须在1到9之间");
        String feedback=request.feedback()==null?"":request.feedback().strip();
        if(feedback.length()>2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"反馈不能超过2000字");
        mapper.complete(userId,sessionId,request.elapsedSeconds(),request.psychologicalEffort(),feedback,LocalDateTime.now(clock));
        return view(mapper.findSession(userId,sessionId));
    }

    public List<SummaryView> summary(String conditionKey){return mapper.summary(conditionKey).stream().map(row->new SummaryView(row.conditionKey(),row.sampleCount(),round(row.meanPreScore()),round(row.meanPostScore()),round(row.meanGain()),round(row.meanEffort()),round(row.meanElapsedSeconds()),row.feedbackCount())).toList();}

    private EvaluationMapper.SessionRow owned(long userId,long id){var row=mapper.findSession(userId,id);if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"评估会话不存在或不属于当前用户");return row;}
    private SessionView view(EvaluationMapper.SessionRow row){return new SessionView(row.getId(),row.getConditionKey(),row.getTaskKey(),row.getConsentVersion(),row.getStartedAt(),row.getCompletedAt(),row.getElapsedSeconds(),row.getPsychologicalEffort(),row.getFeedback());}
    private String write(List<String> responses){try{return json.writeValueAsString(responses==null?List.of():responses);}catch(Exception e){throw new IllegalStateException("无法保存测量答案",e);}}
    private static Double round(Double value){return value==null?null:Math.round(value*100.0)/100.0;}

    public record StartRequest(String conditionKey,String taskKey,String consentVersion,boolean consentAccepted) { }
    public record MeasureRequest(String phase,double score,List<String> responses) { }
    public record CompleteRequest(int elapsedSeconds,int psychologicalEffort,String feedback) { }
    public record SessionView(Long id,String conditionKey,String taskKey,String consentVersion,LocalDateTime startedAt,LocalDateTime completedAt,Integer elapsedSeconds,Integer psychologicalEffort,String feedback) { }
    public record SummaryView(String conditionKey,Long sampleCount,Double meanPreScore,Double meanPostScore,Double meanGain,Double meanEffort,Double meanElapsedSeconds,Long feedbackCount) { }
}
