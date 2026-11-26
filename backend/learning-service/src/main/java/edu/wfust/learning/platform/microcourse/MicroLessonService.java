package edu.wfust.learning.platform.microcourse;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.admin.AdminPayloads;
import edu.wfust.learning.platform.course.content.CourseStudyMapper;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.entity.MicroLessonDraftEntity;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.knowledge.PythonGraphClient;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.MicroLessonMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MicroLessonService {
    private final CourseMapper courses;
    private final CourseStudyMapper study;
    private final KnowledgePointMapper points;
    private final QuizQuestionMapper questions;
    private final MicroLessonMapper drafts;
    private final PythonGraphClient graph;
    private final AiMicroLessonClient ai;
    private final ObjectMapper json;
    private final Clock clock;

    public MicroLessonService(CourseMapper courses, CourseStudyMapper study, KnowledgePointMapper points,
                              QuizQuestionMapper questions, MicroLessonMapper drafts, PythonGraphClient graph,
                              AiMicroLessonClient ai, ObjectMapper json, Clock clock) {
        this.courses = courses; this.study = study; this.points = points; this.questions = questions;
        this.drafts = drafts; this.graph = graph; this.ai = ai; this.json = json; this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DraftView generate(Long userId, String authorization, GenerateRequest request) {
        CourseEntity course = enabledCourse(request == null ? null : request.courseId());
        if (request.sectionId() == null || request.sectionId() <= 0) throw bad("请选择课程小节");
        CourseStudyMapper.SectionRow section = study.section(course.getId(), request.sectionId());
        if (section == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程小节不存在或已停用");
        KnowledgePointEntity point = points.selectById(section.knowledgePointId());
        if (point == null || !course.getId().equals(point.getCourseId()) || !Boolean.TRUE.equals(point.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程小节知识点不存在或已停用");
        }

        PythonGraphClient.PythonGraphResponse graphResponse = graph.retrieve(section.graphEntity(), 12);
        if (graphResponse == null || !graphResponse.hasEvidence() || graphResponse.triples() == null || graphResponse.triples().isEmpty()) {
            throw bad("图谱没有返回足够证据，暂不生成微课草稿");
        }
        List<String> objectives = readList(section.objectivesJson(), new TypeReference<List<String>>() { });
        List<TripleView> triples = graphResponse.triples().stream()
            .map(item -> new TripleView(item.subject(), item.predicate(), item.object(), item.score())).toList();
        List<CommonErrorView> commonErrors = (drafts.commonErrors(point.getId()) == null ? List.<MicroLessonMapper.CommonErrorRow>of() : drafts.commonErrors(point.getId()))
            .stream().map(item -> new CommonErrorView(item.stem(), item.explanation(), item.wrongCount() == null ? 0 : item.wrongCount())).toList();
        List<SelfTestView> selfTests = questions.selectList(new QueryWrapper<QuizQuestionEntity>()
                .eq("knowledge_point_id", point.getId()).eq("enabled", true).orderByAsc("id").last("LIMIT 3"))
            .stream().map(this::selfTest).toList();
        List<PrerequisiteView> prerequisites = prerequisites(point);
        SourceBundle source = new SourceBundle(section.title(), objectives, section.contentJson(), triples, commonErrors, selfTests, prerequisites);
        String generated = ai.generate(authorization, source).text();

        var row = new MicroLessonDraftEntity();
        row.setCourseId(course.getId()); row.setSectionId(section.id()); row.setTitle(section.title()); row.setStatus("DRAFT");
        row.setSummary(generated); row.setGeneratedText(generated); row.setObjectivesJson(writeJson(objectives));
        row.setTriplesJson(writeJson(triples)); row.setCommonErrorsJson(writeJson(commonErrors)); row.setSelfTestJson(writeJson(selfTests));
        row.setPrerequisitesJson(writeJson(prerequisites));
        row.setEvidenceJson(writeJson(new EvidenceView(course.getId(), section.id(), point.getId(), section.graphEntity(), triples.size(), LocalDateTime.now(clock))));
        row.setCreatedBy(userId);
        drafts.insert(row);
        return view(row, section.title());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<DraftView> list(Long courseId) {
        if (courseId != null) course(courseId);
        var query = new QueryWrapper<MicroLessonDraftEntity>().orderByDesc("id");
        if (courseId != null) query.eq("course_id", courseId);
        return drafts.selectList(query).stream().map(row -> view(row, sectionTitle(row))).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DraftView publish(Long id) {
        MicroLessonDraftEntity current = draft(id);
        if (!"DRAFT".equals(current.getStatus())) throw bad("只有草稿微课可以发布");
        drafts.findPublished(current.getCourseId(), current.getSectionId(), id);
        drafts.archivePublished(current.getCourseId(), current.getSectionId(), id);
        LocalDateTime publishedAt = LocalDateTime.now(clock);
        drafts.markPublished(id, publishedAt);
        current.setStatus("PUBLISHED"); current.setPublishedAt(publishedAt);
        return view(current, sectionTitle(current));
    }

    @PreAuthorize("isAuthenticated()")
    public DraftView published(Long courseId, Long sectionId) {
        enabledCourse(courseId);
        MicroLessonDraftEntity row = drafts.published(courseId, sectionId);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "本小节暂未发布微课");
        return view(row, sectionTitle(row));
    }

    private SelfTestView selfTest(QuizQuestionEntity question) {
        return new SelfTestView(question.getId(), question.getStem(), readList(question.getOptionsJson(), new TypeReference<List<AdminPayloads.Option>>() { }));
    }

    private List<PrerequisiteView> prerequisites(KnowledgePointEntity point) {
        List<Long> ids = readList(point.getPrerequisitesJson(), new TypeReference<List<Long>>() { });
        return ids.stream().map(points::selectById).filter(item -> item != null && Boolean.TRUE.equals(item.getEnabled()))
            .map(item -> new PrerequisiteView(item.getId(), item.getName(), item.getGraphEntity())).toList();
    }

    private DraftView view(MicroLessonDraftEntity row, String title) {
        return new DraftView(row.getId(), row.getCourseId(), row.getSectionId(), title, row.getStatus(), row.getSummary(), row.getGeneratedText(),
            readList(row.getObjectivesJson(), new TypeReference<List<String>>() { }), readList(row.getTriplesJson(), new TypeReference<List<TripleView>>() { }),
            readList(row.getCommonErrorsJson(), new TypeReference<List<CommonErrorView>>() { }), readList(row.getSelfTestJson(), new TypeReference<List<SelfTestView>>() { }),
            readList(row.getPrerequisitesJson(), new TypeReference<List<PrerequisiteView>>() { }), readObject(row.getEvidenceJson(), new TypeReference<EvidenceView>() { }),
            row.getCreatedAt(), row.getPublishedAt());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<SectionOption> sections(Long courseId) {
        course(courseId);
        Map<Long, String> chapterNames = study.chapters(courseId).stream()
            .collect(java.util.stream.Collectors.toMap(CourseStudyMapper.ChapterRow::id, CourseStudyMapper.ChapterRow::name, (a, b) -> a));
        return study.sections(courseId).stream()
            .map(s -> new SectionOption(s.id(), chapterNames.getOrDefault(s.chapterId(), "课程章节"), s.title(),
                s.knowledgePointId(), s.knowledgePointName(), s.graphEntity()))
            .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DraftView updateDraft(Long id, UpdateDraftRequest request) {
        MicroLessonDraftEntity row = draft(id);
        if (!"DRAFT".equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅草稿状态微课允许编辑");
        }
        String nextTitle = request.title().strip();
        String nextText = request.generatedText().strip();
        if (nextTitle.isEmpty() || nextText.isEmpty()) {
            throw bad("微课标题和正文不能为空");
        }
        row.setTitle(nextTitle);
        row.setGeneratedText(nextText);
        row.setSummary(nextText.length() > 120 ? nextText.substring(0, 120) : nextText);
        row.setUpdatedAt(LocalDateTime.now(clock));
        drafts.updateById(row);
        return view(row, sectionTitle(row));
    }

    private CourseEntity course(Long id) {
        CourseEntity row = id == null ? null : courses.selectById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在");
        return row;
    }

    private String sectionTitle(MicroLessonDraftEntity row) {
        if (row.getTitle() != null && !row.getTitle().isBlank()) return row.getTitle();
        var section = study.section(row.getCourseId(), row.getSectionId());
        return section == null ? "课程微课" : section.title();
    }

    private MicroLessonDraftEntity draft(Long id) {
        MicroLessonDraftEntity row = id == null ? null : drafts.selectById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "微课草稿不存在");
        return row;
    }

    private CourseEntity enabledCourse(Long id) {
        CourseEntity row = id == null ? null : courses.selectById(id);
        if (row == null || !Boolean.TRUE.equals(row.getEnabled())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在或已停用");
        return row;
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    private <T> List<T> readList(String value, TypeReference<List<T>> type) {
        if (value == null || value.isBlank()) return List.of();
        return json.readValue(value, type);
    }

    private <T> T readObject(String value, TypeReference<T> type) {
        if (value == null || value.isBlank()) return null;
        return json.readValue(value, type);
    }

    private String writeJson(Object value) { return json.writeValueAsString(value); }

    public record GenerateRequest(@NotNull @Positive Long courseId, @NotNull @Positive Long sectionId) { }
    public record UpdateDraftRequest(@NotBlank @jakarta.validation.constraints.Size(max = 160) String title,
                                     @NotBlank @jakarta.validation.constraints.Size(max = 12000) String generatedText) { }
    public record SectionOption(Long id, String chapterName, String title, Long knowledgePointId, String knowledgePointName, String graphEntity) { }
    public record SourceBundle(String title, List<String> objectives, String contentJson, List<TripleView> triples,
                               List<CommonErrorView> commonErrors, List<SelfTestView> selfTests, List<PrerequisiteView> prerequisites) { }
    public record TripleView(String subject, String predicate, String object, double score) { }
    public record CommonErrorView(String stem, String explanation, long wrongCount) { }
    public record SelfTestView(Long questionId, String stem, List<AdminPayloads.Option> options) { }
    public record PrerequisiteView(Long id, String name, String graphEntity) { }
    public record EvidenceView(Long courseId, Long sectionId, Long knowledgePointId, String graphEntity, int tripleCount, LocalDateTime generatedAt) { }
    public record DraftView(Long id, Long courseId, Long sectionId, String title, String status, String summary, String generatedText,
                            List<String> objectives, List<TripleView> triples, List<CommonErrorView> commonErrors,
                            List<SelfTestView> selfTests, List<PrerequisiteView> prerequisites, EvidenceView evidence,
                            LocalDateTime createdAt, LocalDateTime publishedAt) { }
}
