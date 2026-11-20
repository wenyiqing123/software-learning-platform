package edu.wfust.learning.platform.evaluation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/evaluations")
@PreAuthorize("hasRole('ADMIN')")
public class EvaluationAdminController {
    private final EvaluationService service;
    public EvaluationAdminController(EvaluationService service){this.service=service;}
    @GetMapping("/summary") public List<EvaluationService.SummaryView> summary(@RequestParam(required=false) String conditionKey){return service.summary(conditionKey);}
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    public String export(@RequestParam(required=false) String conditionKey){
        StringBuilder csv=new StringBuilder("conditionKey,sampleCount,meanPreScore,meanPostScore,meanGain,meanEffort,meanElapsedSeconds,feedbackCount\n");
        for(var row:service.summary(conditionKey)) csv.append(csv(row.conditionKey())).append(',').append(row.sampleCount()).append(',').append(value(row.meanPreScore())).append(',').append(value(row.meanPostScore())).append(',').append(value(row.meanGain())).append(',').append(value(row.meanEffort())).append(',').append(value(row.meanElapsedSeconds())).append(',').append(row.feedbackCount()).append('\n');
        return csv.toString();
    }
    private String csv(String value){return "\""+(value==null?"":value.replace("\"","\"\""))+"\"";}
    private String value(Double value){return value==null?"":value.toString();}
}
