package edu.wfust.learning.platform.recommendation;

import edu.wfust.learning.platform.entity.*;
import edu.wfust.learning.platform.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecommendationServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Asia/Shanghai"));
    final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    final KnowledgeMasteryMapper mastery = mock(KnowledgeMasteryMapper.class);
    final WrongQuestionMapper wrong = mock(WrongQuestionMapper.class);
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    RecommendationService service() { return new RecommendationService(points, mastery, wrong, redis, new ObjectMapper(), mock(RecommendationSnapshotMapper.class), FIXED_CLOCK); }
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

    @Test void malformedUserCacheFallsBackToFreshUserScopedCalculationAndRefreshesCache() {
        var valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("slp:recommendation:9")).thenReturn("not-json");
        when(points.selectList(any())).thenReturn(List.of(point(1, "需求", "[]", 1)));
        when(mastery.selectList(any())).thenReturn(List.of());
        when(wrong.countByKnowledgePoint(9L)).thenReturn(List.of());
        var result = service().forUser(9L);
        assertThat(result).isNotEmpty();
        verify(valueOps).set(eq("slp:recommendation:9"), anyString(), any(java.time.Duration.class));
    }

    @Test void incorporatesExplainableTimeDecayUrgencyIntoReasonAndUsesPeakForPrerequisites() {
        var prereq = point(1, "需求分析", "[]", 1);
        var advanced = point(2, "数据流图", "[1]", 2);
        when(points.selectList(any())).thenReturn(List.of(prereq, advanced));

        var decayedPrereq = mastery(1, 55);
        decayedPrereq.setPeakMastery(BigDecimal.valueOf(80));
        decayedPrereq.setLastStudiedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(14));
        var recentAdvanced = mastery(2, 70);
        recentAdvanced.setPeakMastery(BigDecimal.valueOf(70));
        recentAdvanced.setLastStudiedAt(LocalDateTime.now(FIXED_CLOCK));
        when(mastery.selectList(any())).thenReturn(List.of(decayedPrereq, recentAdvanced));
        when(wrong.countByKnowledgePoint(9L)).thenReturn(List.of());

        var result = service().forUser(9L);
        var decayedRec = result.stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
        var advancedRec = result.stream().filter(r -> r.id().equals(2L)).findFirst().orElseThrow();

        assertThat(decayedRec.kind()).isEqualTo("遗忘预警");
        assertThat(decayedRec.reviewUrgency()).isEqualTo("DUE");
        assertThat(decayedRec.daysSinceLastPractice()).isEqualTo(14L);
        assertThat(decayedRec.reason()).contains("14 天").contains("记忆保持估值");
        assertThat(decayedRec.reason()).doesNotContain("机器学习").doesNotContain("BKT").doesNotContain("DKT");
        // 先修峰值 80 >= 60，故进阶节点不会被误标为“前置待补”
        assertThat(advancedRec.kind()).isNotEqualTo("前置待补");
    }

    static KnowledgePointEntity point(long id, String name, String prerequisites, int order) {
        var p = new KnowledgePointEntity(); p.setId(id); p.setName(name); p.setGraphEntity(name); p.setPrerequisitesJson(prerequisites); p.setSortOrder(order); p.setEnabled(true); return p;
    }
    static KnowledgeMasteryEntity mastery(long pointId, int score) { var m = new KnowledgeMasteryEntity(); m.setKnowledgePointId(pointId); m.setMasteryScore(BigDecimal.valueOf(score)); m.setAttemptCount(5); return m; }
}
