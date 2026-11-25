package edu.wfust.learning.platform.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.knowledge.MasteryRetentionPolicy;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Validated
public class CourseController {
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgeMasteryMapper masteryMapper;
    private final Clock clock;
    private final MasteryRetentionPolicy retentionPolicy = new MasteryRetentionPolicy();

    public CourseController(CourseMapper courseMapper, KnowledgePointMapper knowledgePointMapper, KnowledgeMasteryMapper masteryMapper) {
        this(courseMapper, knowledgePointMapper, masteryMapper, MasteryRetentionPolicy.DEFAULT_CLOCK);
    }

    @Autowired
    public CourseController(CourseMapper courseMapper, KnowledgePointMapper knowledgePointMapper,
                            KnowledgeMasteryMapper masteryMapper, Clock clock) {
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.masteryMapper = masteryMapper;
        this.clock = clock == null ? MasteryRetentionPolicy.DEFAULT_CLOCK : clock;
    }

    @GetMapping("/courses")
    public List<CourseView> courses() {
        return courseMapper.selectList(new QueryWrapper<CourseEntity>().eq("enabled", true).orderByAsc("id")).stream()
                .map(course -> new CourseView(course.getId(), course.getCode(), course.getName(), course.getTerm(), course.getDescription(), course.getGraphSpaceId()))
                .toList();
    }

    @GetMapping("/knowledge/points")
    public List<KnowledgePointView> knowledgePoints(@RequestParam(required = false) Long courseId, Authentication authentication) {
        QueryWrapper<KnowledgePointEntity> query = new QueryWrapper<KnowledgePointEntity>().eq("enabled", true)
                .inSql("course_id", "SELECT id FROM course WHERE enabled = 1").orderByAsc("sort_order");
        if (courseId != null) query.eq("course_id", courseId);
        long userId = Long.parseLong(authentication.getName());
        Map<Long, MasteryRetentionPolicy.RetentionSnapshot> retentionByPoint = masteryMapper
                .selectList(new QueryWrapper<KnowledgeMasteryEntity>().eq("user_id", userId))
                .stream()
                .collect(Collectors.toMap(
                        KnowledgeMasteryEntity::getKnowledgePointId,
                        row -> retentionPolicy.evaluate(row, clock),
                        (first, second) -> second
                ));
        return knowledgePointMapper.selectList(query).stream()
                .map(point -> {
                    MasteryRetentionPolicy.RetentionSnapshot snapshot =
                            retentionByPoint.getOrDefault(point.getId(), MasteryRetentionPolicy.RetentionSnapshot.EMPTY);
                    return new KnowledgePointView(
                            point.getId(),
                            point.getCourseId(),
                            point.getChapterId(),
                            point.getName(),
                            point.getGraphEntity(),
                            point.getDescription(),
                            point.getSortOrder(),
                            snapshot.currentMastery(),
                            snapshot.peakMastery(),
                            snapshot.effectiveMastery(),
                            snapshot.daysSinceLastPractice(),
                            snapshot.reviewUrgency().name()
                    );
                })
                .toList();
    }

    public record CourseView(Long id, String code, String name, String term, String description, Integer graphSpaceId) { }

    public record KnowledgePointView(Long id, Long courseId, Long chapterId, String name, String graphEntity,
                                     String description, Integer sortOrder, double mastery,
                                     Double peakMastery, Double effectiveMastery, Long daysSinceLastPractice,
                                     String reviewUrgency) {
        public KnowledgePointView(Long id, Long courseId, Long chapterId, String name, String graphEntity,
                                  String description, Integer sortOrder, double mastery) {
            this(id, courseId, chapterId, name, graphEntity, description, sortOrder, mastery,
                    mastery, mastery, 0L, "FRESH");
        }
    }
}
