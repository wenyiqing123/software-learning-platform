package edu.wfust.learning.platform.recommendation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.*;
import edu.wfust.learning.platform.knowledge.MasteryRetentionPolicy;
import edu.wfust.learning.platform.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final KnowledgePointMapper points;
    private final KnowledgeMasteryMapper masteryMapper;
    private final WrongQuestionMapper wrongMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final RecommendationSnapshotMapper snapshots;
    private final Clock clock;
    private final MasteryRetentionPolicy retentionPolicy = new MasteryRetentionPolicy();

    public RecommendationService(KnowledgePointMapper points, KnowledgeMasteryMapper masteryMapper,
                                 WrongQuestionMapper wrongMapper, StringRedisTemplate redis, ObjectMapper json,
                                 RecommendationSnapshotMapper snapshots) {
        this(points, masteryMapper, wrongMapper, redis, json, snapshots, MasteryRetentionPolicy.DEFAULT_CLOCK);
    }

    @Autowired
    public RecommendationService(KnowledgePointMapper points, KnowledgeMasteryMapper masteryMapper,
                                 WrongQuestionMapper wrongMapper, StringRedisTemplate redis, ObjectMapper json,
                                 RecommendationSnapshotMapper snapshots, Clock clock) {
        this.points = points;
        this.masteryMapper = masteryMapper;
        this.wrongMapper = wrongMapper;
        this.redis = redis;
        this.json = json;
        this.snapshots = snapshots;
        this.clock = clock == null ? MasteryRetentionPolicy.DEFAULT_CLOCK : clock;
    }

    @Transactional public List<Recommendation> forUser(long userId) {
        String key="slp:recommendation:"+userId;
        try{String cached=redis.opsForValue().get(key);if(cached!=null)return json.readValue(cached,new TypeReference<>(){});}catch(RuntimeException ignored){}
        Map<Long,KnowledgeMasteryEntity> mastery=masteryMapper.selectList(new QueryWrapper<KnowledgeMasteryEntity>().eq("user_id",userId)).stream().collect(Collectors.toMap(KnowledgeMasteryEntity::getKnowledgePointId,m->m));
        Map<Long,MasteryRetentionPolicy.RetentionSnapshot> retentionByPoint=new HashMap<>();
        mastery.forEach((pointId,row)->retentionByPoint.put(pointId,retentionPolicy.evaluate(row,clock)));
        Map<Long,Integer> errors=new HashMap<>();
        wrongMapper.countByKnowledgePoint(userId).forEach(row->{Number id=(Number)row.get("knowledgePointId");if(id!=null)errors.put(id.longValue(),((Number)row.get("wrongCount")).intValue());});
        var all=points.selectList(new QueryWrapper<KnowledgePointEntity>().eq("enabled",true).orderByAsc("sort_order").orderByAsc("id"));
        Map<Long,KnowledgePointEntity> byId=all.stream().collect(Collectors.toMap(KnowledgePointEntity::getId,p->p));
        Map<Long,List<Long>> blockers=new HashMap<>();var prerequisiteFor=new HashMap<Long,List<String>>();
        for(var p:all){var missing=prerequisites(p).stream().filter(byId::containsKey).filter(id->unlockMastery(retentionByPoint.get(id))<60).toList();blockers.put(p.getId(),missing);for(Long id:missing)prerequisiteFor.computeIfAbsent(id,k->new ArrayList<>()).add(p.getName());}
        var result=all.stream().map(p->{
            var m=mastery.get(p.getId());
            MasteryRetentionPolicy.RetentionSnapshot snap=m==null?MasteryRetentionPolicy.RetentionSnapshot.EMPTY:retentionByPoint.getOrDefault(p.getId(),MasteryRetentionPolicy.RetentionSnapshot.EMPTY);
            double learned=m==null?0.0:snap.effectiveMastery();
            double peak=m==null?0.0:snap.peakMastery();
            long days=m==null?0L:snap.daysSinceLastPractice();
            String urgency=m==null?MasteryRetentionPolicy.ReviewUrgency.FRESH.name():snap.reviewUrgency().name();
            int wrong=errors.getOrDefault(p.getId(),0);
            double recency=m==null||m.getLastStudiedAt()==null?70:Math.min(100,Math.max(0,days*12.0));
            double repetition=m==null?50:Math.min(100,35+Optional.ofNullable(m.getAttemptCount()).orElse(0)*5.0);
            double sequence=100.0/(1+Math.max(0,Optional.ofNullable(p.getSortOrder()).orElse(1)-1));
            double decayBoost="DUE".equals(urgency)?12.0:"FADING".equals(urgency)?6.0:0.0;
            double score=(100-learned)*.45+Math.min(100,wrong*20.0)*.25+recency*.15+repetition*.10+sequence*.05+decayBoost;
            String kind=m==null?"入门":"DUE".equals(urgency)?"遗忘预警":"巩固";
            String reason;
            if("DUE".equals(urgency)){
                reason=(wrong>0?"关联未掌握错题累计 "+wrong+" 次，且":"")
                        +"距上次练习已 "+days+" 天，当前记忆保持估值回落至 "+Math.round(learned)+"%（历史峰值 "+Math.round(peak)+"%），建议优先复习。";
            }else if("FADING".equals(urgency)){
                reason=(wrong>0?"关联未掌握错题累计 "+wrong+" 次，且":"")
                        +"距上次练习已 "+days+" 天，当前记忆保持估值回落至 "+Math.round(learned)+"%，建议优先复习巩固。";
            }else{
                reason=wrong>0?"关联未掌握错题累计 "+wrong+" 次，建议结合图谱复习。":m==null?"尚无答题记录，按课程顺序建立基础。":"掌握度 "+Math.round(learned)+"%，结合近期练习安排复习。";
            }
            var missing=blockers.getOrDefault(p.getId(),List.of());
            if(!missing.isEmpty()){score*=.35;kind="前置待补";reason="建议先学习前置知识："+missing.stream().map(id->byId.get(id).getName()).collect(Collectors.joining("、"))+"。";}
            else if(prerequisiteFor.containsKey(p.getId())){
                score=Math.max(score,85+(100-learned)*.1);kind="补齐基础";
                if("DUE".equals(urgency)||"FADING".equals(urgency)){
                    reason="作为 "+String.join("、",prerequisiteFor.get(p.getId()))+" 的前置知识，距上次练习已 "+days+" 天，当前记忆保持估值回落至 "+Math.round(learned)+"%，建议优先复习。";
                }else{
                    reason="作为 "+String.join("、",prerequisiteFor.get(p.getId()))+" 的前置知识，当前掌握度 "+Math.round(learned)+"%，优先补齐。";
                }
            }
            return new Recommendation(p.getId(),p.getName(),p.getGraphEntity(),kind,Math.round(Math.min(100,Math.max(0,score))*100)/100.0,reason,peak,learned,days,urgency);
        }).sorted(Comparator.comparingDouble(Recommendation::score).reversed().thenComparing(Recommendation::id)).limit(12).toList();
        snapshots.deleteForUser(userId);result.forEach(r->snapshots.save(userId,r.id(),r.score(),r.reason()));
        try{redis.opsForValue().set(key,json.writeValueAsString(result),Duration.ofMinutes(5));}catch(RuntimeException ignored){}
        return result;
    }
    private List<Long> prerequisites(KnowledgePointEntity point){try{return point.getPrerequisitesJson()==null?List.of():json.readValue(point.getPrerequisitesJson(),new TypeReference<>(){});}catch(RuntimeException invalid){return List.of();}}
    private double unlockMastery(MasteryRetentionPolicy.RetentionSnapshot snapshot){return snapshot==null?0:Math.max(snapshot.peakMastery(),snapshot.currentMastery());}
    public record Recommendation(Long id,String title,String knowledgePoint,String kind,double score,String reason,Double peakMastery,Double effectiveMastery,Long daysSinceLastPractice,String reviewUrgency){
        public Recommendation(Long id,String title,String knowledgePoint,String kind,double score,String reason){
            this(id,title,knowledgePoint,kind,score,reason,null,null,null,null);
        }
    }
}
