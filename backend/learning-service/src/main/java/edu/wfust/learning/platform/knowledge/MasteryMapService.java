package edu.wfust.learning.platform.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.MasteryMapMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MasteryMapService {
    private static final double THRESHOLD = MasteryRetentionPolicy.MASTERED_THRESHOLD;
    private final CourseMapper courses;
    private final KnowledgePointMapper points;
    private final KnowledgeMasteryMapper mastery;
    private final MasteryMapMapper due;
    private final ObjectMapper json;
    private final Clock clock;
    private final MasteryRetentionPolicy retentionPolicy = new MasteryRetentionPolicy();

    public MasteryMapService(CourseMapper courses, KnowledgePointMapper points, KnowledgeMasteryMapper mastery,
                             MasteryMapMapper due, ObjectMapper json) {
        this(courses, points, mastery, due, json, MasteryRetentionPolicy.DEFAULT_CLOCK);
    }

    @Autowired
    public MasteryMapService(CourseMapper courses, KnowledgePointMapper points, KnowledgeMasteryMapper mastery,
                             MasteryMapMapper due, ObjectMapper json, Clock clock) {
        this.courses = courses;
        this.points = points;
        this.mastery = mastery;
        this.due = due;
        this.json = json;
        this.clock = clock == null ? MasteryRetentionPolicy.DEFAULT_CLOCK : clock;
    }

    public MapView map(Long courseId, Long userId) {
        List<CourseEntity> selectedCourses;
        String courseName;
        if (courseId == null) {
            selectedCourses = courses.selectList(new QueryWrapper<CourseEntity>().eq("enabled", true).orderByAsc("id"));
            if (selectedCourses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有可用课程");
            courseName = "全部启用课程";
        } else {
            CourseEntity course = courses.selectById(courseId);
            if (course == null || !Boolean.TRUE.equals(course.getEnabled())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在或已停用");
            selectedCourses = List.of(course);
            courseName = course.getName();
        }
        List<Long> courseIds = selectedCourses.stream().map(CourseEntity::getId).toList();
        QueryWrapper<KnowledgePointEntity> pointQuery = new QueryWrapper<KnowledgePointEntity>().in("course_id", courseIds).eq("enabled", true).orderByAsc("course_id", "sort_order", "id");
        List<KnowledgePointEntity> rows = points.selectList(pointQuery);
        Map<Long, KnowledgePointEntity> byId = new LinkedHashMap<>(); rows.forEach(row -> byId.put(row.getId(), row));
        Map<Long, Score> scoreByPoint = new HashMap<>();
        for (KnowledgeMasteryEntity row : mastery.selectList(new QueryWrapper<KnowledgeMasteryEntity>().eq("user_id", userId))) {
            if (row.getKnowledgePointId() != null && byId.containsKey(row.getKnowledgePointId())) {
                MasteryRetentionPolicy.RetentionSnapshot snapshot = retentionPolicy.evaluate(row, clock);
                scoreByPoint.put(row.getKnowledgePointId(), new Score(
                        snapshot.currentMastery(),
                        snapshot.peakMastery(),
                        snapshot.effectiveMastery(),
                        snapshot.daysSinceLastPractice(),
                        snapshot.reviewUrgency().name(),
                        value(row.getAttemptCount()),
                        value(row.getCorrectCount())
                ));
            }
        }
        Map<Long, Long> dueByPoint = new HashMap<>();
        due.dueCounts(userId).forEach(row -> dueByPoint.put(row.getPointId(), row.getDueCount() == null ? 0L : row.getDueCount()));
        Map<Long, List<Long>> prerequisites = new LinkedHashMap<>(); byId.values().forEach(row -> prerequisites.put(row.getId(), prerequisiteIds(row, byId)));
        List<Node> nodes = rows.stream().map(row -> {
            Score score = scoreByPoint.getOrDefault(row.getId(), Score.ZERO);
            String status = status(score, prerequisites.getOrDefault(row.getId(), List.of()), scoreByPoint);
            return new Node(row.getId(), row.getCourseId(), row.getChapterId(), row.getName(), row.getGraphEntity(),
                    score.mastery(), score.attempts(), score.correct(), dueByPoint.getOrDefault(row.getId(), 0L), status,
                    prerequisites.getOrDefault(row.getId(), List.of()),
                    score.peakMastery(), score.effectiveMastery(), score.daysSinceLastPractice(), score.reviewUrgency());
        }).toList();
        List<Edge> edges = prerequisites.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(source -> new Edge(source, entry.getKey(), "先修"))).toList();
        return new MapView(courseId, courseName, nodes, edges);
    }

    private List<Long> prerequisiteIds(KnowledgePointEntity point, Map<Long, KnowledgePointEntity> byId) {
        if (point.getPrerequisitesJson() == null || point.getPrerequisitesJson().isBlank()) return List.of();
        try {
            JsonNode root = json.readTree(point.getPrerequisitesJson()); if (root == null || !root.isArray()) return List.of();
            Set<Long> unique = new HashSet<>(); List<Long> result = new ArrayList<>();
            for (JsonNode value : root) if (value.isNumber() && value.asDouble() == value.asLong() && value.asLong() != point.getId() && byId.containsKey(value.asLong()) && unique.add(value.asLong())) result.add(value.asLong());
            result.sort(Comparator.comparing((Long id) -> byId.get(id).getSortOrder(), Comparator.nullsLast(Integer::compareTo)).thenComparingLong(Long::longValue));
            return result;
        } catch (RuntimeException ignored) { return List.of(); }
    }
    private String status(Score score, List<Long> required, Map<Long, Score> scores) {
        double unlockValue = score.unlockMastery();
        if (unlockValue >= THRESHOLD) return "MASTERED";
        if (unlockValue > 0) return "IN_PROGRESS";
        return required.stream().anyMatch(id -> scores.getOrDefault(id, Score.ZERO).unlockMastery() < THRESHOLD) ? "BLOCKED" : "UP_NEXT";
    }
    private int value(Integer value) { return value == null ? 0 : value; }
    private record Score(double mastery, double peakMastery, double effectiveMastery, long daysSinceLastPractice,
                         String reviewUrgency, int attempts, int correct) {
        static final Score ZERO = new Score(0.0, 0.0, 0.0, 0L, "FRESH", 0, 0);
        double unlockMastery() { return Math.max(peakMastery, mastery); }
    }
    public record MapView(Long courseId, String courseName, List<Node> nodes, List<Edge> edges) { }
    public record Node(Long id, Long courseId, Long chapterId, String name, String graphEntity, double mastery,
                       int attemptCount, int correctCount, long dueReviewCount, String status, List<Long> prerequisiteIds,
                       Double peakMastery, Double effectiveMastery, Long daysSinceLastPractice, String reviewUrgency) {
        public Node(Long id, Long courseId, Long chapterId, String name, String graphEntity, double mastery,
                    int attemptCount, int correctCount, long dueReviewCount, String status, List<Long> prerequisiteIds) {
            this(id, courseId, chapterId, name, graphEntity, mastery, attemptCount, correctCount, dueReviewCount, status,
                    prerequisiteIds, mastery, mastery, 0L, "FRESH");
        }
    }
    public record Edge(Long source, Long target, String relation) { }
}
