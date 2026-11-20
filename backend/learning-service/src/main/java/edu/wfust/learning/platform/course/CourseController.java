package edu.wfust.learning.platform.course;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
public class CourseController {
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgeMasteryMapper masteryMapper;

    public CourseController(CourseMapper courseMapper, KnowledgePointMapper knowledgePointMapper, KnowledgeMasteryMapper masteryMapper) {
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.masteryMapper = masteryMapper;
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
        java.util.Map<Long, Double> masteryByPoint = masteryMapper.selectList(new QueryWrapper<KnowledgeMasteryEntity>().eq("user_id", userId))
                .stream().collect(java.util.stream.Collectors.toMap(KnowledgeMasteryEntity::getKnowledgePointId, item -> item.getMasteryScore().doubleValue()));
        return knowledgePointMapper.selectList(query).stream()
                .map(point -> new KnowledgePointView(point.getId(), point.getCourseId(), point.getChapterId(), point.getName(),
                        point.getGraphEntity(), point.getDescription(), point.getSortOrder(), masteryByPoint.getOrDefault(point.getId(), 0.0)))
                .toList();
    }

    public record CourseView(Long id, String code, String name, String term, String description, Integer graphSpaceId) { }
    public record KnowledgePointView(Long id, Long courseId, Long chapterId, String name, String graphEntity, String description, Integer sortOrder, double mastery) { }
}
