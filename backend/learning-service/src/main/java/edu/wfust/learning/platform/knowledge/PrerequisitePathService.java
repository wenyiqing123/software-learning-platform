package edu.wfust.learning.platform.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PrerequisitePathService {
    private static final double MASTERY_THRESHOLD = 60.0;

    private final CourseMapper courses;
    private final KnowledgePointMapper points;
    private final KnowledgeMasteryMapper mastery;
    private final ObjectMapper json;

    public PrerequisitePathService(CourseMapper courses, KnowledgePointMapper points,
                                  KnowledgeMasteryMapper mastery, ObjectMapper json) {
        this.courses = courses;
        this.points = points;
        this.mastery = mastery;
        this.json = json;
    }

    public LearningPath path(Long targetId, Long userId) {
        KnowledgePointEntity target = points.selectById(targetId);
        if (target == null || !Boolean.TRUE.equals(target.getEnabled())) notFound();
        CourseEntity course = courses.selectById(target.getCourseId());
        if (course == null || !Boolean.TRUE.equals(course.getEnabled())) notFound();

        List<KnowledgePointEntity> queried = points.selectList(new QueryWrapper<KnowledgePointEntity>()
                .eq("course_id", target.getCourseId()).eq("enabled", true)
                .orderByAsc("sort_order", "id"));
        List<KnowledgePointEntity> rows = queried.stream()
                .filter(point -> target.getCourseId().equals(point.getCourseId()))
                .filter(point -> Boolean.TRUE.equals(point.getEnabled()))
                .toList();
        Map<Long, KnowledgePointEntity> byId = new LinkedHashMap<>();
        rows.forEach(point -> byId.put(point.getId(), point));
        if (!byId.containsKey(targetId)) notFound();

        Map<Long, List<Long>> prerequisites = new LinkedHashMap<>();
        byId.values().forEach(point -> prerequisites.put(point.getId(), validPrerequisites(point, byId)));
        List<Long> orderedIds = new ArrayList<>();
        Map<Long, Integer> visitState = new HashMap<>();
        boolean[] cycleDetected = {false};
        visit(targetId, prerequisites, byId, visitState, new HashSet<>(), orderedIds, cycleDetected);

        Map<Long, Double> masteryByPoint = new HashMap<>();
        for (KnowledgeMasteryEntity row : mastery.selectList(
                new QueryWrapper<KnowledgeMasteryEntity>().eq("user_id", userId))) {
            masteryByPoint.put(row.getKnowledgePointId(), score(row));
        }

        List<PathNode> pathNodes = orderedIds.stream().map(id -> {
            KnowledgePointEntity point = byId.get(id);
            double value = masteryByPoint.getOrDefault(id, 0.0);
            List<Long> required = prerequisites.getOrDefault(id, List.of());
            String status = status(value, required, masteryByPoint);
            return new PathNode(point.getId(), point.getCourseId(), point.getChapterId(), point.getName(),
                    point.getGraphEntity(), value, required, status);
        }).toList();
        return new LearningPath(targetId, target.getCourseId(), pathNodes, cycleDetected[0]);
    }

    private List<Long> validPrerequisites(KnowledgePointEntity point, Map<Long, KnowledgePointEntity> byId) {
        if (point.getPrerequisitesJson() == null || point.getPrerequisitesJson().isBlank()) return List.of();
        try {
            JsonNode root = json.readTree(point.getPrerequisitesJson());
            if (root == null || !root.isArray()) return List.of();
            Set<Long> unique = new HashSet<>();
            List<Long> result = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isNumber() || item.asDouble() != item.asLong()) continue;
                long prerequisiteId = item.asLong();
                if (prerequisiteId == point.getId()) continue;
                KnowledgePointEntity prerequisite = byId.get(prerequisiteId);
                if (prerequisite != null && unique.add(prerequisiteId)) result.add(prerequisiteId);
            }
            result.sort(Comparator.comparing((Long id) -> byId.get(id).getSortOrder(),
                            Comparator.nullsLast(Integer::compareTo))
                    .thenComparingLong(Long::longValue));
            return result;
        } catch (RuntimeException invalidJson) {
            return List.of();
        }
    }

    private void visit(Long pointId, Map<Long, List<Long>> prerequisites,
                       Map<Long, KnowledgePointEntity> byId, Map<Long, Integer> visitState,
                       Set<Long> included, List<Long> orderedIds, boolean[] cycleDetected) {
        int state = visitState.getOrDefault(pointId, 0);
        if (state == 1) {
            cycleDetected[0] = true;
            return;
        }
        if (state == 2 || !included.add(pointId)) return;
        visitState.put(pointId, 1);
        for (Long prerequisiteId : prerequisites.getOrDefault(pointId, List.of())) {
            if (!byId.containsKey(prerequisiteId)) continue;
            if (visitState.getOrDefault(prerequisiteId, 0) == 1) {
                cycleDetected[0] = true;
                continue;
            }
            visit(prerequisiteId, prerequisites, byId, visitState, included, orderedIds, cycleDetected);
        }
        visitState.put(pointId, 2);
        orderedIds.add(pointId);
    }

    private String status(double value, List<Long> required, Map<Long, Double> masteryByPoint) {
        if (value >= MASTERY_THRESHOLD) return "MASTERED";
        if (value > 0) return "IN_PROGRESS";
        boolean missingPrerequisite = required.stream()
                .anyMatch(id -> masteryByPoint.getOrDefault(id, 0.0) < MASTERY_THRESHOLD);
        return missingPrerequisite ? "BLOCKED" : "UP_NEXT";
    }

    private double score(KnowledgeMasteryEntity row) {
        if (row == null || row.getMasteryScore() == null) return 0.0;
        return Math.max(0.0, Math.min(100.0, row.getMasteryScore().doubleValue()));
    }

    private void notFound() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程或知识点不存在或已停用");
    }

    public record LearningPath(Long targetId, Long courseId, List<PathNode> nodes, boolean cycleDetected) { }

    public record PathNode(Long id, Long courseId, Long chapterId, String name, String graphEntity,
                           double mastery, List<Long> prerequisiteIds, String status) { }
}
