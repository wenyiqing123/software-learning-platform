package edu.wfust.learning.platform.course.content;

import edu.wfust.learning.platform.mapper.CourseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;

@Service
@PreAuthorize("isAuthenticated()")
public class CourseStudyService {
    private final CourseMapper courses;
    private final CourseStudyMapper content;
    private final ObjectMapper json;
    public CourseStudyService(CourseMapper courses, CourseStudyMapper content, ObjectMapper json) {
        this.courses = courses; this.content = content; this.json = json;
    }
    public StudyView study(Long courseId, Long userId) {
        var course = enabledCourse(courseId);
        var completed = new HashSet<>(content.completed(userId, courseId));
        var rows = content.sections(courseId);
        var sections = rows.stream().map(s -> new SectionView(s.id(), s.knowledgePointId(), s.knowledgePointName(),
            s.graphEntity(), s.title(), read(s.objectivesJson(), new TypeReference<List<String>>() { }),
            read(s.contentJson(), new TypeReference<List<ContentBlock>>() { }),
            read(s.resourcesJson(), new TypeReference<List<Resource>>() { }), completed.contains(s.id()))).toList();
        var chapterViews = content.chapters(courseId).stream().map(ch -> new ChapterView(ch.id(), ch.name(), ch.description(),
            java.util.stream.IntStream.range(0, rows.size()).filter(i -> ch.id().equals(rows.get(i).chapterId())).mapToObj(sections::get).toList())).toList();
        return new StudyView(course.getId(), course.getName(), course.getCode(), course.getTerm(), course.getDescription(),
            sections.size(), (int) sections.stream().filter(SectionView::completed).count(), chapterViews);
    }
    @Transactional
    public ProgressView saveProgress(Long courseId, Long sectionId, Long userId, boolean completed) {
        enabledCourse(courseId);
        if (content.sections(courseId).stream().noneMatch(s -> s.id().equals(sectionId)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "本课程中不存在此学习小节");
        content.saveProgress(userId, sectionId, completed);
        return new ProgressView(sectionId, completed);
    }
    private edu.wfust.learning.platform.entity.CourseEntity enabledCourse(Long courseId) {
        var course = courses.selectById(courseId);
        if (course == null || !Boolean.TRUE.equals(course.getEnabled())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在或已停用");
        return course;
    }
    private <T> T read(String value, TypeReference<T> type) { return json.readValue(value, type); }
    public record Resource(String title, String url, String description) { }
    public record ContentBlock(String heading, List<String> paragraphs) { }
    public record SectionView(Long id, Long knowledgePointId, String knowledgePointName, String graphEntity, String title,
        List<String> objectives, List<ContentBlock> content, List<Resource> resources, boolean completed) { }
    public record ChapterView(Long id, String name, String description, List<SectionView> sections) { }
    public record StudyView(Long courseId, String name, String code, String term, String description,
        int totalSections, int completedSections, List<ChapterView> chapters) { }
    public record ProgressView(Long sectionId, boolean completed) { }
}
