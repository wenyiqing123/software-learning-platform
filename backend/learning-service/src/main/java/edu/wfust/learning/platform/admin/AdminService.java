package edu.wfust.learning.platform.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.*;
import edu.wfust.learning.platform.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {
    private final CourseMapper courses;
    private final QuizQuestionMapper questions;
    private final KnowledgePointMapper points;
    private final ObjectMapper json;
    public AdminService(CourseMapper courses, QuizQuestionMapper questions, KnowledgePointMapper points, ObjectMapper json) {
        this.courses = courses; this.questions = questions; this.points = points; this.json = json;
    }
    public List<CourseEntity> courses() { return courses.selectList(new QueryWrapper<CourseEntity>().orderByAsc("id")); }
    public List<PointView> knowledgePoints(Long courseId) {
        course(courseId);
        return points.selectList(new QueryWrapper<KnowledgePointEntity>().eq("course_id", courseId).orderByAsc("sort_order", "id"))
            .stream().map(this::pointView).toList();
    }
    @Transactional
    public PointView createKnowledgePoint(AdminPayloads.KnowledgePointRequest request) {
        var c = course(request.courseId());
        if (!Boolean.TRUE.equals(c.getEnabled())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先启用所属课程");
        List<Long> prereqIds = validatePrerequisites(request.courseId(), null, request.prerequisiteIds());
        var row = new KnowledgePointEntity();
        copyPoint(request, prereqIds, row);
        row.setEnabled(true);
        try { points.insert(row); }
        catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "同一课程下图谱实体已存在"); }
        return pointView(row);
    }
    @Transactional
    public PointView updateKnowledgePoint(Long id, AdminPayloads.KnowledgePointRequest request) {
        var row = knowledgePoint(id);
        if (!row.getCourseId().equals(request.courseId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持跨课程迁移知识点");
        List<Long> prereqIds = validatePrerequisites(row.getCourseId(), id, request.prerequisiteIds());
        copyPoint(request, prereqIds, row);
        try { points.updateById(row); }
        catch (DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "同一课程下图谱实体已存在"); }
        return pointView(row);
    }
    @Transactional
    public PointView setKnowledgePointEnabled(Long id, boolean enabled) {
        var row = knowledgePoint(id);
        row.setEnabled(enabled);
        points.updateById(row);
        return pointView(row);
    }
    @Transactional
    public CourseEntity createCourse(AdminPayloads.CourseRequest request) {
        ensureUniqueCourse(request, null);
        var row = new CourseEntity(); copyCourse(request, row); row.setEnabled(true);
        try { courses.insert(row); } catch (DuplicateKeyException e) { throw conflict(); }
        return row;
    }
    @Transactional
    public CourseEntity updateCourse(Long id, AdminPayloads.CourseRequest request) {
        var row = course(id); ensureUniqueCourse(request, id); copyCourse(request, row);
        try { courses.updateById(row); } catch (DuplicateKeyException e) { throw conflict(); }
        return row;
    }
    @Transactional
    public CourseEntity setCourseEnabled(Long id, boolean enabled) {
        var row = course(id); row.setEnabled(enabled); courses.updateById(row); return row;
    }
    public List<QuestionView> questions(Long courseId) {
        var query = new QueryWrapper<QuizQuestionEntity>().orderByAsc("id");
        if (courseId != null) { course(courseId); query.eq("course_id", courseId); }
        return questions.selectList(query).stream().map(this::view).toList();
    }
    @Transactional
    public QuestionView createQuestion(AdminPayloads.QuestionRequest request) {
        validateMapping(request);
        var row = new QuizQuestionEntity(); copyQuestion(request, row); row.setEnabled(true); row.setSource("ADMIN");
        questions.insert(row); return view(row);
    }
    @Transactional
    public QuestionView updateQuestion(Long id, AdminPayloads.QuestionRequest request) {
        var row = question(id); validateMapping(request); copyQuestion(request, row); questions.updateById(row); return view(row);
    }
    @Transactional
    public QuestionView setQuestionEnabled(Long id, boolean enabled) {
        var row = question(id);
        if (enabled) {
            // Re-enabling must obey the same mapping/payload rules as creation.
            validateMapping(new AdminPayloads.QuestionRequest(row.getCourseId(), row.getKnowledgePointId(), row.getQuestionType(), row.getStem(),
                read(row.getOptionsJson(), new TypeReference<List<AdminPayloads.Option>>() { }),
                read(row.getAnswerJson(), new TypeReference<List<String>>() { }), row.getExplanation(), row.getDifficulty()));
        }
        row.setEnabled(enabled); questions.updateById(row); return view(row);
    }
    private void validateMapping(AdminPayloads.QuestionRequest request) {
        QuestionPayloadValidator.validate(request);
        var course = course(request.courseId());
        if (!Boolean.TRUE.equals(course.getEnabled())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先启用所属课程");
        var point = points.selectById(request.knowledgePointId());
        if (point == null || !request.courseId().equals(point.getCourseId()) || !Boolean.TRUE.equals(point.getEnabled()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识点必须属于同一课程且处于启用状态");
    }
    private void copyCourse(AdminPayloads.CourseRequest r, CourseEntity row) {
        row.setCode(r.code().trim()); row.setName(r.name().trim()); row.setTerm(r.term().trim());
        row.setDescription(r.description().trim()); row.setGraphSpaceId(1);
    }
    private void ensureUniqueCourse(AdminPayloads.CourseRequest r, Long id) {
        var query = new QueryWrapper<CourseEntity>().eq("code", r.code().trim()).eq("term", r.term().trim());
        if (id != null) query.ne("id", id);
        if (courses.selectCount(query) > 0) throw conflict();
    }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "相同课程代码与学期已存在"); }
    private void copyQuestion(AdminPayloads.QuestionRequest r, QuizQuestionEntity row) {
        row.setCourseId(r.courseId()); row.setKnowledgePointId(r.knowledgePointId()); row.setQuestionType(r.type());
        row.setStem(r.stem().trim()); row.setExplanation(r.explanation().trim()); row.setDifficulty(r.difficulty());
        row.setOptionsJson(json.writeValueAsString(r.options().stream().map(o -> new AdminPayloads.Option(o.key(), o.text().trim())).toList()));
        row.setAnswerJson(json.writeValueAsString(r.answer()));
    }
    private CourseEntity course(Long id) {
        var row = id == null ? null : courses.selectById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在"); return row;
    }
    private KnowledgePointEntity knowledgePoint(Long id) {
        var row = id == null ? null : points.selectById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识点不存在");
        return row;
    }
    private List<Long> validatePrerequisites(Long courseId, Long selfId, List<Long> requested) {
        if (requested == null || requested.isEmpty()) return List.of();
        java.util.LinkedHashSet<Long> unique = new java.util.LinkedHashSet<>();
        for (Long pid : requested) {
            if (pid == null || pid <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "先修知识点 ID 必须为正整数");
            if (selfId != null && selfId.equals(pid)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识点不能将自身设为先修");
            var prereq = points.selectById(pid);
            if (prereq == null || !courseId.equals(prereq.getCourseId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "先修知识点必须属于同一课程");
            unique.add(pid);
        }
        return List.copyOf(unique);
    }
    private void copyPoint(AdminPayloads.KnowledgePointRequest r, List<Long> prereqIds, KnowledgePointEntity row) {
        row.setCourseId(r.courseId());
        row.setName(r.name().trim());
        row.setGraphEntity(r.graphEntity().trim());
        row.setDescription(r.description() == null ? "" : r.description().trim());
        row.setSortOrder(r.sortOrder() == null ? 0 : r.sortOrder());
        row.setPrerequisitesJson(prereqIds.isEmpty() ? null : json.writeValueAsString(prereqIds));
    }
    private List<Long> readPrerequisiteIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try { return read(raw, new TypeReference<List<Long>>() { }); }
        catch (RuntimeException ignored) { return List.of(); }
    }
    private PointView pointView(KnowledgePointEntity p) {
        return new PointView(p.getId(), p.getCourseId(), p.getName(), p.getGraphEntity(), p.getEnabled(),
            p.getDescription() == null ? "" : p.getDescription(), p.getSortOrder() == null ? 0 : p.getSortOrder(), readPrerequisiteIds(p.getPrerequisitesJson()));
    }
    private QuizQuestionEntity question(Long id) {
        var row = questions.selectById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在"); return row;
    }
    private <T> T read(String value, TypeReference<T> type) { return json.readValue(value, type); }
    private QuestionView view(QuizQuestionEntity row) {
        return new QuestionView(row.getId(), row.getCourseId(), row.getKnowledgePointId(), row.getQuestionType(), row.getStem(),
            read(row.getOptionsJson(), new TypeReference<List<AdminPayloads.Option>>() { }),
            read(row.getAnswerJson(), new TypeReference<List<String>>() { }), row.getExplanation(), row.getDifficulty(), row.getEnabled(), row.getSource());
    }
    public record PointView(Long id, Long courseId, String name, String graphEntity, Boolean enabled,
        String description, Integer sortOrder, List<Long> prerequisiteIds) {
        public PointView(Long id, Long courseId, String name, String graphEntity, Boolean enabled) {
            this(id, courseId, name, graphEntity, enabled, "", 0, List.of());
        }
    }
    /** This DTO (including the answer) is only returned by ADMIN-authorized methods. */
    public record QuestionView(Long id, Long courseId, Long knowledgePointId, String type, String stem,
        List<AdminPayloads.Option> options, List<String> answer, String explanation, Integer difficulty, Boolean enabled, String source) { }
}
