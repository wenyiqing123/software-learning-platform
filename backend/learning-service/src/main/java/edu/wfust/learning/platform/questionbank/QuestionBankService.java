package edu.wfust.learning.platform.questionbank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.admin.AdminPayloads;
import edu.wfust.learning.platform.admin.QuestionPayloadValidator;
import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.entity.QuestionBankImportEntity;
import edu.wfust.learning.platform.entity.QuestionBankVersionEntity;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.mapper.CourseMapper;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.QuestionBankImportMapper;
import edu.wfust.learning.platform.mapper.QuestionBankVersionMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class QuestionBankService {
    private final CourseMapper courses;
    private final KnowledgePointMapper points;
    private final QuizQuestionMapper questions;
    private final QuestionBankVersionMapper versions;
    private final QuestionBankImportMapper imports;
    private final ObjectMapper json;
    private final Clock clock;

    public QuestionBankService(CourseMapper courses, KnowledgePointMapper points, QuizQuestionMapper questions,
                               QuestionBankVersionMapper versions, QuestionBankImportMapper imports,
                               ObjectMapper json, Clock clock) {
        this.courses = courses;
        this.points = points;
        this.questions = questions;
        this.versions = versions;
        this.imports = imports;
        this.json = json;
        this.clock = clock;
    }

    public List<VersionView> listVersions(Long courseId) {
        if (courseId != null) course(courseId);
        var query = new QueryWrapper<QuestionBankVersionEntity>().orderByDesc("id");
        if (courseId != null) query.eq("course_id", courseId);
        return versions.selectList(query).stream().map(this::view).toList();
    }

    @Transactional
    public VersionView createVersion(Long userId, CreateVersionRequest request) {
        if (request == null || request.courseId() == null || request.courseId() <= 0) {
            throw bad("请选择所属课程");
        }
        CourseEntity course = course(request.courseId());
        if (!Boolean.TRUE.equals(course.getEnabled())) throw bad("请先启用所属课程");
        if (request.versionName() == null || request.versionName().isBlank() || request.versionName().trim().length() > 120) {
            throw bad("版本名称不能为空或过长");
        }
        var row = new QuestionBankVersionEntity();
        row.setCourseId(request.courseId());
        row.setVersionName(request.versionName().trim());
        row.setStatus("DRAFT");
        row.setSourceFilename(request.sourceFilename() == null ? "" : request.sourceFilename().trim());
        row.setCreatedBy(userId);
        versions.insert(row);
        return view(row);
    }

    @Transactional
    public ImportResult importQuestions(Long versionId, Long userId, ImportRequest request) {
        QuestionBankVersionEntity version = version(versionId);
        if (!"DRAFT".equals(version.getStatus())) throw bad("只有草稿版本可以导入题目");
        if (request == null || request.questions() == null || request.questions().isEmpty()) {
            throw bad("导入题目不能为空");
        }
        CourseEntity course = course(version.getCourseId());
        if (!Boolean.TRUE.equals(course.getEnabled())) throw bad("请先启用所属课程");

        List<ImportError> errors = new ArrayList<>();
        int success = 0;
        for (int index = 0; index < request.questions().size(); index++) {
            ImportRow row = request.questions().get(index);
            try {
                validateRow(version, course, row);
                QuizQuestionEntity question = toQuestion(version, row);
                questions.insert(question);
                success++;
            } catch (RuntimeException exception) {
                errors.add(new ImportError(index + 1, reason(exception)));
            }
        }

        var batch = new QuestionBankImportEntity();
        batch.setVersionId(versionId);
        batch.setSourceFilename(version.getSourceFilename() == null ? "" : version.getSourceFilename());
        batch.setTotalRows(request.questions().size());
        batch.setSuccessCount(success);
        batch.setErrorCount(errors.size());
        batch.setErrorDetailsJson(writeJson(errors));
        batch.setCreatedBy(userId);
        imports.insert(batch);
        return new ImportResult(batch.getId(), versionId, request.questions().size(), success, errors.size(), errors);
    }

    @Transactional
    public VersionView publishVersion(Long versionId) {
        QuestionBankVersionEntity current = version(versionId);
        if (!"DRAFT".equals(current.getStatus())) throw bad("只有草稿版本可以发布");
        if (versions.countQuestions(versionId) <= 0) throw bad("版本中没有可发布的合法题目");

        List<QuestionBankVersionEntity> oldVersions = versions.findPublishedByCourse(current.getCourseId(), versionId);
        versions.archivePublished(current.getCourseId(), versionId);
        for (QuestionBankVersionEntity old : oldVersions) {
            questions.disableVersionQuestions(old.getId());
        }
        questions.enableVersionQuestions(versionId);
        LocalDateTime publishedAt = LocalDateTime.now(clock);
        versions.markPublished(versionId, publishedAt);
        current.setStatus("PUBLISHED");
        current.setPublishedAt(publishedAt);
        return view(current);
    }

    private void validateRow(QuestionBankVersionEntity version, CourseEntity course, ImportRow row) {
        if (row == null) throw bad("题目行不能为空");
        if (!version.getCourseId().equals(row.courseId())) throw bad("题目课程必须与版本所属课程一致");
        var request = new AdminPayloads.QuestionRequest(row.courseId(), row.knowledgePointId(), row.type(), row.stem(),
            row.options(), row.answer(), row.explanation(), row.difficulty());
        QuestionPayloadValidator.validate(request);
        KnowledgePointEntity point = points.selectById(row.knowledgePointId());
        if (point == null || !version.getCourseId().equals(point.getCourseId()) || !Boolean.TRUE.equals(point.getEnabled())) {
            throw bad("知识点必须属于同一课程且处于启用状态");
        }
        if (!Boolean.TRUE.equals(course.getEnabled())) throw bad("请先启用所属课程");
    }

    private QuizQuestionEntity toQuestion(QuestionBankVersionEntity version, ImportRow row) {
        var question = new QuizQuestionEntity();
        question.setCourseId(version.getCourseId());
        question.setKnowledgePointId(row.knowledgePointId());
        question.setQuestionType(row.type());
        question.setStem(row.stem().trim());
        question.setOptionsJson(writeJson(row.options().stream()
            .map(option -> new AdminPayloads.Option(option.key(), option.text().trim())).toList()));
        question.setAnswerJson(writeJson(row.answer()));
        question.setExplanation(row.explanation().trim());
        question.setDifficulty(row.difficulty());
        question.setSource("IMPORT");
        question.setVersionId(version.getId());
        question.setEnabled(false);
        return question;
    }

    private VersionView view(QuestionBankVersionEntity row) {
        CourseEntity course = courses.selectById(row.getCourseId());
        return new VersionView(row.getId(), row.getCourseId(), course == null ? "" : course.getName(), row.getVersionName(),
            row.getStatus(), row.getSourceFilename(), row.getCreatedBy(), row.getCreatedAt(), row.getPublishedAt(),
            versions.countQuestions(row.getId()));
    }

    private CourseEntity course(Long id) {
        CourseEntity row = id == null ? null : courses.selectById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在");
        return row;
    }

    private QuestionBankVersionEntity version(Long id) {
        QuestionBankVersionEntity row = id == null ? null : versions.selectById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题库版本不存在");
        return row;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String reason(RuntimeException exception) {
        if (exception instanceof ResponseStatusException status && status.getReason() != null) return status.getReason();
        return exception.getMessage() == null || exception.getMessage().isBlank() ? "题目数据无效" : exception.getMessage();
    }

    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (RuntimeException exception) { throw new IllegalStateException("无法保存导入数据", exception); }
    }

    public record CreateVersionRequest(@NotNull Long courseId, @NotBlank @Size(max = 120) String versionName,
                                       @Size(max = 255) String sourceFilename) { }

    public record ImportRequest(@NotEmpty @Size(max = 1000) List<@NotNull ImportRow> questions) { }

    public record ImportRow(Long courseId, Long knowledgePointId, String type, String stem,
                            List<AdminPayloads.Option> options, List<String> answer,
                            String explanation, Integer difficulty) { }

    public record ImportError(int rowNumber, String message) { }

    public record ImportResult(Long importId, Long versionId, int totalRows, int successCount, int errorCount,
                               List<ImportError> errors) { }

    public record VersionView(Long id, Long courseId, String courseName, String versionName, String status,
                              String sourceFilename, Long createdBy, LocalDateTime createdAt, LocalDateTime publishedAt,
                              long questionCount) { }
}
