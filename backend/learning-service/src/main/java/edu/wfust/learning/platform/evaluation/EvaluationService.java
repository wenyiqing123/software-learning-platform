package edu.wfust.learning.platform.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.mapper.EvaluationMapper;
import edu.wfust.learning.platform.entity.ExperimentAssignmentEntity;
import edu.wfust.learning.platform.mapper.ExperimentAssignmentMapper;
import edu.wfust.learning.platform.entity.AiExperimentEntity;
import edu.wfust.learning.platform.mapper.AiExperimentMapper;
import edu.wfust.learning.platform.experimenttask.ExperimentTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EvaluationService {
    private final EvaluationMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final ExperimentAssignmentMapper assignments;
    private final AiExperimentMapper experimentMapper;
    private final ExperimentTaskService taskService;

    public EvaluationService(EvaluationMapper mapper,ObjectMapper json,Clock clock){this(mapper,json,clock,null,null,null);}
    public EvaluationService(EvaluationMapper mapper,ObjectMapper json,Clock clock,ExperimentAssignmentMapper assignments){this(mapper,json,clock,assignments,null,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public EvaluationService(EvaluationMapper mapper,ObjectMapper json,Clock clock,ExperimentAssignmentMapper assignments,AiExperimentMapper experimentMapper,ExperimentTaskService taskService){
        this.mapper=mapper;
        this.json=json;
        this.clock=clock;
        this.assignments=assignments;
        this.experimentMapper=experimentMapper;
        this.taskService=taskService;
    }

    @Transactional
    public SessionView start(long userId,StartRequest request){
        if(!request.consentAccepted()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"必须先阅读并同意评估说明");
        if(request.conditionKey()==null||request.conditionKey().isBlank()||request.taskKey()==null||request.taskKey().isBlank()||request.consentVersion()==null||request.consentVersion().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"评估条件、任务和同意版本不能为空");
        String finalTaskOrder = request.taskOrder() == null ? null : request.taskOrder().strip();
        if(request.assignmentId()!=null){
            if(assignments==null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"实验分配不存在或不属于当前用户");
            var assignment=assignments.selectById(request.assignmentId());
            if(assignment==null||!Long.valueOf(userId).equals(assignment.getUserId()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"实验分配不存在或不属于当前用户");
            var detail=assignments.findDetailForUser(request.assignmentId(),userId);
            if(detail==null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"实验分配不存在或不属于当前用户");
            if(!"OPEN".equalsIgnoreCase(detail.taskStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"实验任务当前未开放");
            if("COMPLETED".equalsIgnoreCase(detail.assignmentStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"实验任务分配已完成");
            String serverOrder=detail.taskOrder();
            if(serverOrder==null||(!"A→B".equals(serverOrder)&&!"B→A".equals(serverOrder)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"实验组未配置有效评估顺序");
            if(finalTaskOrder!=null&&!finalTaskOrder.isEmpty()&&!serverOrder.equals(finalTaskOrder))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"客户端指定的评估顺序与服务端权威顺序不匹配");
            finalTaskOrder=serverOrder;
        }
        var row=new EvaluationMapper.SessionRow(null,userId,request.conditionKey().strip(),request.taskKey().strip(),finalTaskOrder,request.assignmentId(),request.consentVersion().strip(),LocalDateTime.now(clock),null,null,null,null,null);
        mapper.insertSession(row);
        var saved=row.getId()!=null?mapper.findSession(userId,row.getId()):null;
        return view(saved!=null?saved:row);
    }

    public List<SessionView> mine(long userId){
        var sessions=mapper.listOwned(userId);
        var phases=mapper.listOwnedPhaseStatuses(userId);
        var phaseMap=phases==null?Map.<Long,List<String>>of():phases.stream()
                .collect(Collectors.groupingBy(EvaluationMapper.PhaseRow::sessionId,Collectors.mapping(EvaluationMapper.PhaseRow::phase,Collectors.toList())));
        return sessions.stream().map(s->{
            var cp=phaseMap.getOrDefault(s.getId(),List.of());
            return new SessionView(s.getId(),s.getConditionKey(),s.getTaskKey(),s.getTaskOrder(),s.getAssignmentId(),s.getConsentVersion(),s.getStartedAt(),s.getCompletedAt(),s.getElapsedSeconds(),s.getPsychologicalEffort(),s.getFeedback(),cp);
        }).toList();
    }

    @Transactional
    public void measure(long userId,long sessionId,MeasureRequest request){
        owned(userId,sessionId);
        if(!List.of("PRE","POST","DELAYED","EQUIVALENT").contains(request.phase())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"测量阶段必须是 PRE、POST、DELAYED 或 EQUIVALENT");
        if(request.score()<0||request.score()>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"测验得分必须在0到100之间");
        mapper.upsertMeasurement(userId,sessionId,request.phase(),request.score(),write(request.responses()),LocalDateTime.now(clock));
    }

    @Transactional
    public SessionView complete(long userId,long sessionId,CompleteRequest request){
        var row=owned(userId,sessionId);
        if(row.getAssignmentId()!=null){
            if(!mapper.hasMeasurement(userId,sessionId,"PRE")){
                throw new ResponseStatusException(HttpStatus.CONFLICT,"完成实验任务评估前必须先完成前测 (PRE)");
            }
            if(!mapper.hasMeasurement(userId,sessionId,"POST")){
                throw new ResponseStatusException(HttpStatus.CONFLICT,"完成实验任务评估前必须先完成后测 (POST)");
            }
            if(!hasSuccessfulRun(userId,row.getAssignmentId())){
                throw new ResponseStatusException(HttpStatus.CONFLICT,"未找到该任务的已持久化成功实验运行记录");
            }
        }
        LocalDateTime serverStart;
        if(row.getAssignmentId()!=null){
            if(assignments==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"未配置实验分配依赖");
            var detail=assignments.findDetailForUser(row.getAssignmentId(),userId);
            if(detail!=null&&detail.startedAt()!=null){
                serverStart=detail.startedAt();
            } else {
                var entity=assignments.selectById(row.getAssignmentId());
                if(entity==null||!Long.valueOf(userId).equals(entity.getUserId())||entity.getStartedAt()==null){
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"实验分配缺少有效开始时间");
                }
                serverStart=entity.getStartedAt();
            }
        } else {
            serverStart=row.getStartedAt();
        }
        if(serverStart==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"评估会话缺少有效开始时间");
        LocalDateTime now=LocalDateTime.now(clock);
        long elapsed=java.time.Duration.between(serverStart,now).getSeconds();
        if(elapsed<1||elapsed>86400) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"任务用时必须在1秒到24小时之间");
        if(request.psychologicalEffort()<1||request.psychologicalEffort()>9) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"心理努力评分必须在1到9之间");
        String feedback=request.feedback()==null?"":request.feedback().strip();
        if(feedback.length()>2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"反馈不能超过2000字");
        mapper.complete(userId,sessionId,(int)elapsed,request.psychologicalEffort(),feedback,now);
        if(row.getAssignmentId()!=null&&taskService!=null){
            taskService.completeRun(userId,row.getAssignmentId(),true);
        }
        return view(mapper.findSession(userId,sessionId));
    }

    @Transactional
    public void abandon(long userId, long sessionId) {
        var row = owned(userId, sessionId);
        if (row.getCompletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已完成的评估会话不可放弃删除");
        }
        mapper.deleteMeasurements(userId, sessionId);
        mapper.deleteUncompletedSession(userId, sessionId);
    }

    public List<SummaryView> summary(String conditionKey){
        var details=mapper.completedRows(conditionKey);
        if(details!=null&&!details.isEmpty()){
            Map<String,List<EvaluationMapper.DetailRow>> byCondition=details.stream().collect(Collectors.groupingBy(EvaluationMapper.DetailRow::conditionKey, java.util.TreeMap::new, Collectors.toList()));
            return byCondition.entrySet().stream().map(entry -> {
                var rows=entry.getValue().stream().map(row->new EvaluationStatistics.Row(row.userId(),row.conditionKey(),row.taskKey(),row.taskOrder(),row.elapsedSeconds(),row.effort(),row.preScore(),row.postScore(),row.delayedScore())).toList();
                long feedback=entry.getValue().stream().filter(row -> Boolean.TRUE.equals(row.feedbackProvided())).count();
                return toSummary(EvaluationStatistics.summarize(entry.getKey(),rows),feedback);
            }).toList();
        }
        return mapper.summary(conditionKey).stream().map(row->new SummaryView(row.conditionKey(),row.sampleCount(),null,null,null,round(row.meanPreScore()),round(row.meanPostScore()),null,null,null,null,null,null,null,round(row.meanGain()),round(row.meanEffort()),null,null,null,round(row.meanElapsedSeconds()),null,null,null,row.feedbackCount())).toList();
    }

    public String exportAnonymized(String conditionKey){
        StringBuilder csv=new StringBuilder("participantCode,conditionKey,taskKey,taskOrder,elapsedSeconds,psychologicalEffort,preScore,postScore,delayedScore\n");
        for(var row:mapper.completedRows(conditionKey)) csv.append(csv(EvaluationStatistics.participantCode(row.userId()))).append(',').append(csv(row.conditionKey())).append(',').append(csv(row.taskKey())).append(',').append(csv(row.taskOrder())).append(',').append(value(row.elapsedSeconds())).append(',').append(value(row.effort())).append(',').append(value(row.preScore())).append(',').append(value(row.postScore())).append(',').append(value(row.delayedScore())).append('\n');
        return csv.toString();
    }

    private EvaluationMapper.SessionRow owned(long userId,long id){var row=mapper.findSession(userId,id);if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"评估会话不存在或不属于当前用户");return row;}
    private boolean ownsAssignment(long userId,long assignmentId){ExperimentAssignmentEntity assignment=assignments.selectById(assignmentId);return assignment!=null&&Long.valueOf(userId).equals(assignment.getUserId());}
    private SessionView view(EvaluationMapper.SessionRow row){return new SessionView(row.getId(),row.getConditionKey(),row.getTaskKey(),row.getTaskOrder(),row.getAssignmentId(),row.getConsentVersion(),row.getStartedAt(),row.getCompletedAt(),row.getElapsedSeconds(),row.getPsychologicalEffort(),row.getFeedback());}
    private SummaryView toSummary(EvaluationStatistics.Summary stats,long feedbackCount){return new SummaryView(stats.conditionKey(),(long)stats.sampleCount(),stats.medianPreScore(),stats.stddevPreScore(),stats.iqrPreScore(),stats.meanPreScore(),stats.meanPostScore(),stats.medianPostScore(),stats.stddevPostScore(),stats.iqrPostScore(),stats.meanDelayedScore(),stats.medianDelayedScore(),stats.stddevDelayedScore(),stats.iqrDelayedScore(),stats.meanGain(),stats.meanEffort(),stats.medianEffort(),stats.stddevEffort(),stats.iqrEffort(),stats.meanElapsedSeconds(),stats.medianElapsedSeconds(),stats.stddevElapsedSeconds(),stats.iqrElapsedSeconds(),feedbackCount);}
    private String write(List<String> responses){try{return json.writeValueAsString(responses==null?List.of():responses);}catch(Exception e){throw new IllegalStateException("无法保存测量答案",e);}}
    private static Double round(Double value){return value==null?null:Math.round(value*100.0)/100.0;}
    private static String csv(Object value){if(value==null)return "";String text=String.valueOf(value).replace("\"","\"\"");return text.contains(",")||text.contains("\n")?"\""+text+"\"":text;}
    private static String value(Object value){return value==null?"":String.valueOf(value);}

    private boolean hasSuccessfulRun(long userId, long assignmentId) {
        if (experimentMapper == null) return false;
        var runs = experimentMapper.selectList(new QueryWrapper<AiExperimentEntity>()
                .eq("user_id", userId)
                .eq("assignment_id", assignmentId));
        if (runs == null || runs.isEmpty()) return false;
        for (var run : runs) {
            String jsonStr = run.getResultsJson();
            if (jsonStr == null || jsonStr.isBlank()) continue;
            try {
                List<Map<String, Object>> results = json.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
                if (results != null && results.stream().anyMatch(r -> {
                    Object error = r.get("error");
                    return error == null || String.valueOf(error).isBlank();
                })) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public record StartRequest(String conditionKey,String taskKey,String taskOrder,Long assignmentId,String consentVersion,boolean consentAccepted) { public StartRequest(String conditionKey,String taskKey,String consentVersion,boolean consentAccepted){this(conditionKey,taskKey,null,null,consentVersion,consentAccepted);} }
    public record MeasureRequest(String phase,double score,List<String> responses) { }
    public record CompleteRequest(int elapsedSeconds,int psychologicalEffort,String feedback) { }
    public record SessionView(Long id,String conditionKey,String taskKey,String taskOrder,Long assignmentId,String consentVersion,LocalDateTime startedAt,LocalDateTime completedAt,Integer elapsedSeconds,Integer psychologicalEffort,String feedback,List<String> completedPhases) {
        public SessionView(Long id,String conditionKey,String taskKey,String taskOrder,Long assignmentId,String consentVersion,LocalDateTime startedAt,LocalDateTime completedAt,Integer elapsedSeconds,Integer psychologicalEffort,String feedback){
            this(id,conditionKey,taskKey,taskOrder,assignmentId,consentVersion,startedAt,completedAt,elapsedSeconds,psychologicalEffort,feedback,List.of());
        }
    }
    public record SummaryView(String conditionKey,Long sampleCount,Double medianPreScore,Double stddevPreScore,Double iqrPreScore,Double meanPreScore,Double meanPostScore,Double medianPostScore,Double stddevPostScore,Double iqrPostScore,Double meanDelayedScore,Double medianDelayedScore,Double stddevDelayedScore,Double iqrDelayedScore,Double meanGain,Double meanEffort,Double medianEffort,Double stddevEffort,Double iqrEffort,Double meanElapsedSeconds,Double medianElapsedSeconds,Double stddevElapsedSeconds,Double iqrElapsedSeconds,Long feedbackCount) { }
}
