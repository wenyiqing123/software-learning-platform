package edu.wfust.learning.platform.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PrerequisiteQualityService {
    private final CourseMapper courses;
    private final KnowledgePointMapper points;
    private final ObjectMapper json;

    public PrerequisiteQualityService(CourseMapper courses, KnowledgePointMapper points, ObjectMapper json) {
        this.courses = courses;
        this.points = points;
        this.json = json;
    }

    public QualityReport quality(Long courseId) {
        CourseEntity course = courses.selectById(courseId);
        if (course == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在");

        List<KnowledgePointEntity> allPoints = points.selectList(new QueryWrapper<KnowledgePointEntity>().orderByAsc("id"));
        List<KnowledgePointEntity> coursePoints = allPoints.stream()
                .filter(point -> courseId.equals(point.getCourseId()))
                .toList();
        Map<Long, KnowledgePointEntity> byId = new LinkedHashMap<>();
        allPoints.forEach(point -> byId.put(point.getId(), point));
        Map<Long, List<Long>> graph = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();
        int validEdges = 0;

        for (KnowledgePointEntity point : coursePoints) {
            ParsedPrerequisites parsed = parse(point, issues);
            Set<Long> unique = new LinkedHashSet<>();
            for (Long prerequisiteId : parsed.ids()) {
                if (!unique.add(prerequisiteId)) {
                    issues.add(issue("DUPLICATE_REFERENCE", "WARNING", point, prerequisiteId,
                            byId.get(prerequisiteId), "先修知识点被重复引用。"));
                    continue;
                }
                KnowledgePointEntity prerequisite = byId.get(prerequisiteId);
                if (prerequisite == null) {
                    issues.add(issue("MISSING_REFERENCE", "ERROR", point, prerequisiteId, null,
                            "引用的先修知识点不存在。"));
                    continue;
                }
                if (!courseId.equals(prerequisite.getCourseId())) {
                    issues.add(issue("CROSS_COURSE_REFERENCE", "ERROR", point, prerequisiteId, prerequisite,
                            "先修知识点属于其他课程。"));
                    continue;
                }
                if (point.getId().equals(prerequisiteId)) {
                    issues.add(issue("SELF_REFERENCE", "ERROR", point, prerequisiteId, prerequisite,
                            "知识点不能把自己配置为先修知识点。"));
                    continue;
                }
                graph.computeIfAbsent(point.getId(), ignored -> new ArrayList<>()).add(prerequisiteId);
                validEdges++;
            }
        }
        detectCycles(graph, byId, issues);
        long errorCount = issues.stream().filter(issue -> "ERROR".equals(issue.severity())).count();
        long warningCount = issues.stream().filter(issue -> "WARNING".equals(issue.severity())).count();
        return new QualityReport(courseId, coursePoints.size(), validEdges, errorCount, warningCount,
                errorCount == 0, List.copyOf(issues));
    }

    private ParsedPrerequisites parse(KnowledgePointEntity point, List<Issue> issues) {
        String raw = point.getPrerequisitesJson();
        if (raw == null || raw.isBlank()) return new ParsedPrerequisites(List.of());
        final JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (RuntimeException exception) {
            issues.add(issue("INVALID_JSON", "ERROR", point, null, null, "先修关系不是有效 JSON。"));
            return new ParsedPrerequisites(List.of());
        }
        if (root == null || !root.isArray()) {
            issues.add(issue("INVALID_SHAPE", "ERROR", point, null, null, "先修关系必须是数字 ID 数组。"));
            return new ParsedPrerequisites(List.of());
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : root) {
            if (!item.isNumber() || item.asDouble() != item.asLong() || item.asLong() <= 0) {
                issues.add(issue("INVALID_ID", "ERROR", point, null, null, "先修关系包含非正整数知识点 ID。"));
                continue;
            }
            ids.add(item.asLong());
        }
        return new ParsedPrerequisites(ids);
    }

    private void detectCycles(Map<Long, List<Long>> graph, Map<Long, KnowledgePointEntity> byId, List<Issue> issues) {
        Map<Long, Integer> states = new HashMap<>();
        Set<String> reported = new HashSet<>();
        for (Long pointId : graph.keySet()) {
            detectCycles(pointId, graph, byId, states, new ArrayDeque<>(), reported, issues);
        }
    }

    private void detectCycles(Long pointId, Map<Long, List<Long>> graph, Map<Long, KnowledgePointEntity> byId,
                              Map<Long, Integer> states, Deque<Long> stack, Set<String> reported, List<Issue> issues) {
        if (states.getOrDefault(pointId, 0) == 2) return;
        if (states.getOrDefault(pointId, 0) == 1) return;
        states.put(pointId, 1);
        stack.push(pointId);
        for (Long prerequisiteId : graph.getOrDefault(pointId, List.of())) {
            int state = states.getOrDefault(prerequisiteId, 0);
            if (state == 0) {
                detectCycles(prerequisiteId, graph, byId, states, stack, reported, issues);
            } else if (state == 1) {
                String key = pointId + "->" + prerequisiteId;
                if (reported.add(key)) {
                    issues.add(issue("CYCLE", "ERROR", byId.get(pointId), prerequisiteId, byId.get(prerequisiteId),
                            "先修关系形成循环依赖。"));
                }
            }
        }
        stack.pop();
        states.put(pointId, 2);
    }

    private Issue issue(String code, String severity, KnowledgePointEntity point, Long relatedId,
                        KnowledgePointEntity related, String message) {
        return new Issue(code, severity, point == null ? null : point.getId(), point == null ? null : point.getName(),
                relatedId, related == null ? null : related.getName(), message);
    }

    private record ParsedPrerequisites(List<Long> ids) { }

    public record QualityReport(Long courseId, int pointCount, int validEdgeCount, long errorCount,
                                long warningCount, boolean valid, List<Issue> issues) { }

    public record Issue(String code, String severity, Long pointId, String pointName, Long relatedPointId,
                        String relatedPointName, String message) { }
}
