package edu.wfust.learning.platform.practice;

import edu.wfust.learning.platform.mapper.AdaptivePracticeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class AdaptivePracticeService {
    private final AdaptivePracticeMapper mapper;
    private final ObjectMapper json;
    public AdaptivePracticeService(AdaptivePracticeMapper mapper,ObjectMapper json){this.mapper=mapper;this.json=json;}
    public List<Item> recommend(long userId,Long courseId,int limit){
        if(limit<1||limit>30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"专项练习数量必须在1到30之间");
        var rows=mapper.candidates(userId,courseId,Math.min(100,limit*3));
        Map<Long,Double> mastery=new HashMap<>();
        for(var row:rows) mastery.put(row.getKnowledgePointId(),row.getMastery()==null?0:row.getMastery());
        return rows.stream().map(row->{
            double score=(100-(row.getMastery()==null?0:row.getMastery()))*.5+(row.getWrongCount()==null?0:row.getWrongCount())*12+(Boolean.TRUE.equals(row.getDue())?25:0)+(row.getDifficulty()==null?2:row.getDifficulty())*2;
            List<Long> prerequisites=readPrerequisites(row.getPrerequisitesJson());
            List<Long> blocked=prerequisites.stream().filter(id->mastery.getOrDefault(id,0.0)<60).toList();
            String reason=Boolean.TRUE.equals(row.getDue())?"该题关联错题已到复习时间":row.getWrongCount()!=null&&row.getWrongCount()>0?"该题关联错题累计答错 "+row.getWrongCount()+" 次":row.getMastery()!=null&&row.getMastery()<60?"关联知识点掌握度为 "+Math.round(row.getMastery())+"%":"该知识点暂缺少稳定答题记录";
            if(!blocked.isEmpty()) reason+="；先修知识点尚未完全掌握";
            return new Item(row.getQuestionId(),row.getStem(),row.getKnowledgePointId(),row.getKnowledgePointName(),row.getDifficulty(),row.getMastery(),row.getWrongCount(),Boolean.TRUE.equals(row.getDue()),Math.round(score*100)/100.0,reason);
        }).sorted(Comparator.comparingDouble(Item::score).reversed().thenComparing(Item::questionId)).limit(limit).toList();
    }
    private List<Long> readPrerequisites(String value){if(value==null||value.isBlank())return List.of();try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return List.of();}}
    public record Item(Long questionId,String stem,Long knowledgePointId,String knowledgePointName,Integer difficulty,Double mastery,Integer wrongCount,boolean due,double score,String reason){}
}
