package edu.wfust.learning.platform.experiment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import edu.wfust.learning.platform.entity.AiExperimentEntity;
import edu.wfust.learning.platform.mapper.AiExperimentMapper;
import edu.wfust.learning.platform.experimenttask.ExperimentTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/experiments")
public class AiExperimentController {
    private final AiExperimentMapper mapper;
    private final ObjectMapper json;
    private final AiComparisonClient ai;
    private final ExperimentMetrics metrics;
    private final ExperimentTaskService taskService;
    private final ExperimentExport exporter;
    public AiExperimentController(AiExperimentMapper mapper,ObjectMapper json,AiComparisonClient ai,ExperimentMetrics metrics,ExperimentTaskService taskService,ExperimentExport exporter){this.mapper=mapper;this.json=json;this.ai=ai;this.metrics=metrics;this.taskService=taskService;this.exporter=exporter;}
    public record RunRequest(@NotBlank @Size(max=2000)String question,Boolean includeAblations,@Size(max=4000)String referenceAnswer,@Size(max=40)List<@NotBlank @Size(max=100)String> expectedEntities,String experimentType,Long taskId){
        public RunRequest {
            if(includeAblations==null) includeAblations=false;
            if(experimentType==null || experimentType.isBlank()) experimentType="METHODS";
        }
    }
    public record RatingRequest(@NotEmpty @Size(max=8)Map<@NotBlank String,@Min(1)@Max(5)Integer> ratings){}
    public record ExperimentView(String id,String question,String createdAt,List<Map<String,Object>> results,Map<String,Integer> ratings,Map<String,Object> evaluation,Long taskId,Long assignmentId){}
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping public List<ExperimentView> list(Authentication auth){return mapper.selectList(new QueryWrapper<AiExperimentEntity>().eq("user_id",userId(auth)).orderByDesc("created_at").last("LIMIT 100")).stream().map(this::view).toList();}
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8") public String exportCsv(Authentication auth){return ExperimentExport.csv(exporter.rows(list(auth)));}
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value="/export/json",produces="application/json") public Map<String,Object> exportJson(Authentication auth){return Map.of("schemaVersion","experiment-export-v2","exportedAt",LocalDateTime.now().toString(),"runs",exporter.rows(list(auth)));}

    @PostMapping("/run") @ResponseStatus(HttpStatus.CREATED)
    public ExperimentView run(Authentication auth,@RequestHeader("Authorization")String authorization,@Valid @RequestBody RunRequest request){
        String experimentType = request.experimentType();
        if (!Set.of("METHODS", "QWEN_MATRIX").contains(experimentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"实验类型无效");
        }
        if (experimentType.equals("QWEN_MATRIX") && Boolean.TRUE.equals(request.includeAblations())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Qwen 2×3 矩阵实验不包含既有方法消融条件");
        }
        Long assignmentId=request.taskId()==null?null:taskService.bindRun(userId(auth),request.taskId());
        var results=experimentType.equals("QWEN_MATRIX")
                ? ai.compareQwenMatrix(request.question().strip(),authorization)
                : ai.compare(request.question().strip(),Boolean.TRUE.equals(request.includeAblations()),authorization);
        results=sanitizeResults(results);
        var perMode=new LinkedHashMap<String,Object>();
        for(var result:results){String mode=String.valueOf(result.getOrDefault("resultKey",result.get("mode")));Object error=result.get("error");if(error==null||String.valueOf(error).isBlank())perMode.put(mode,metrics.evaluate(String.valueOf(result.getOrDefault("answer","")),request.referenceAnswer(),request.expectedEntities()));}
        var evaluation=new LinkedHashMap<String,Object>();
        evaluation.put("source","SERVER_AI_SERVICE");evaluation.put("experimentType",experimentType);evaluation.put("referenceAnswer",request.referenceAnswer()==null?"":request.referenceAnswer());evaluation.put("expectedEntities",request.expectedEntities()==null?List.of():request.expectedEntities());evaluation.put("metricsByMode",perMode);evaluation.put("note",experimentType.equals("QWEN_MATRIX")?"Qwen 基座与 LoRA × DIRECT/TRIPLES/RANKED；实体召回率、中文字符级 ROUGE-L/BLEU 与人工评分用于同题配对比较。未审校训练标签的结果属于探索性证据。":"中文字符级 ROUGE-L / BLEU（最高4阶，有效阶数）；实体指标为期望实体召回率；参考答案与人工评分由评测者提供。");
        var entity=new AiExperimentEntity();entity.setUserId(userId(auth));entity.setTaskId(request.taskId());entity.setAssignmentId(assignmentId);entity.setQuestion(request.question().strip());entity.setResultsJson(json.writeValueAsString(results));entity.setRatingsJson("{}");entity.setEvaluationJson(json.writeValueAsString(evaluation));mapper.insert(entity);if(assignmentId!=null)taskService.completeRun(userId(auth),assignmentId,results.stream().anyMatch(result->{Object e=result.get("error");return e==null||String.valueOf(e).isBlank();}));return view(mapper.selectById(entity.getId()));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/ratings") @Transactional
    public ExperimentView rate(Authentication auth,@PathVariable Long id,@Valid @RequestBody RatingRequest request){
        long userId=userId(auth);var entity=mapper.selectOne(new QueryWrapper<AiExperimentEntity>().eq("id",id).eq("user_id",userId));
        if(entity==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"实验记录不存在");
        var existing=view(entity);var allowed=existing.results().stream().filter(r->r.get("error")==null||String.valueOf(r.get("error")).isBlank()).map(r->String.valueOf(r.getOrDefault("resultKey",r.get("mode")))).collect(Collectors.toSet());
        if(!allowed.containsAll(request.ratings().keySet()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"只能为本次成功返回的模式评分");
        var ratings=new HashMap<>(existing.ratings());ratings.putAll(request.ratings());entity.setRatingsJson(json.writeValueAsString(ratings));mapper.update(null,new UpdateWrapper<AiExperimentEntity>().eq("id",id).eq("user_id",userId).set("ratings_json",entity.getRatingsJson()));return view(entity);
    }
    private long userId(Authentication auth){return Long.parseLong(auth.getName());}
    static List<Map<String,Object>> sanitizeResults(List<Map<String,Object>> results){
        return results.stream().map(AiExperimentController::sanitizeResult).toList();
    }
    private static Map<String,Object> sanitizeResult(Map<String,Object> raw){
        var result=new LinkedHashMap<>(raw);
        String mode=String.valueOf(result.getOrDefault("resultKey",result.get("mode")));
        if("RANKED".equalsIgnoreCase(mode)){
            result.remove("prompt");
            result.remove("systemPrompt");
            result.remove("systemPromptBody");
            result.remove("fixedSystemPrompt");
        }
        result.entrySet().removeIf(entry -> {
            String key=entry.getKey().toLowerCase(Locale.ROOT);
            return key.equals("systemprompttext") || key.equals("fixedsystempromptbody");
        });
        return result;
    }
    private ExperimentView view(AiExperimentEntity entity){return new ExperimentView(entity.getId().toString(),entity.getQuestion(),entity.getCreatedAt()==null?LocalDateTime.now().toString():entity.getCreatedAt().toString(),sanitizeResults(json.readValue(entity.getResultsJson(),new TypeReference<>(){})),json.readValue(entity.getRatingsJson(),new TypeReference<>(){}),entity.getEvaluationJson()==null?Map.of("source","LEGACY_BROWSER_IMPORT"):json.readValue(entity.getEvaluationJson(),new TypeReference<>(){}),entity.getTaskId(),entity.getAssignmentId());}
}
