package edu.wfust.learning.platform.recommendation;

import edu.wfust.learning.platform.entity.*;
import edu.wfust.learning.platform.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecommendationServiceTest {
    final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    final KnowledgeMasteryMapper mastery = mock(KnowledgeMasteryMapper.class);
    final WrongQuestionMapper wrong = mock(WrongQuestionMapper.class);
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    RecommendationService service() { return new RecommendationService(points, mastery, wrong, redis, new ObjectMapper(), mock(RecommendationSnapshotMapper.class)); }
    @Test void coldStartRecommendsFoundationBeforeDependentTopic() {
        when(points.selectList(any())).thenReturn(List.of(point(2, "设计", "[1]", 2), point(1, "需求", "[]", 1)));
        when(mastery.selectList(any())).thenReturn(List.of());
        when(wrong.countByKnowledgePoint(9L)).thenReturn(List.of());
        var result = service().forUser(9L);
        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.stream().filter(r -> r.id().equals(2L)).findFirst().orElseThrow().reason()).contains("需求");
    }
    @Test void weakerPrerequisiteIsRecommendedBeforeAlreadyAttemptedAdvancedTopic() {
        when(points.selectList(any())).thenReturn(List.of(point(1, "需求", "[]", 1), point(2, "设计", "[1]", 2)));
        when(mastery.selectList(any())).thenReturn(List.of(mastery(1, 25), mastery(2, 40)));
        when(wrong.countByKnowledgePoint(9L)).thenReturn(List.of(Map.of("knowledgePointId", 2L, "wrongCount", 9L)));
        var result = service().forUser(9L);
        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.getFirst().reason()).contains("前置");
    }
    @Test void resultScoresAreBoundedAndNeverContainDisabledKnowledge() {
        when(points.selectList(any())).thenReturn(List.of(point(1, "需求", "[]", 1)));
        when(mastery.selectList(any())).thenReturn(List.of(mastery(1, 80)));
        when(wrong.countByKnowledgePoint(9L)).thenReturn(List.of(Map.of("knowledgePointId", 1L, "wrongCount", 10000L)));
        assertThat(service().forUser(9L)).allSatisfy(r -> assertThat(r.score()).isBetween(0.0,100.0));
    }
    static KnowledgePointEntity point(long id, String name, String prerequisites, int order) {
        var p = new KnowledgePointEntity(); p.setId(id); p.setName(name); p.setGraphEntity(name); p.setPrerequisitesJson(prerequisites); p.setSortOrder(order); p.setEnabled(true); return p;
    }
    static KnowledgeMasteryEntity mastery(long pointId, int score) { var m = new KnowledgeMasteryEntity(); m.setKnowledgePointId(pointId); m.setMasteryScore(BigDecimal.valueOf(score)); m.setAttemptCount(5); return m; }
}
