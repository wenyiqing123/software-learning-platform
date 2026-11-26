package edu.wfust.learning.platform.admin;

import edu.wfust.learning.platform.entity.CourseEntity;
import edu.wfust.learning.platform.mapper.AdminContentAuditMapper;
import edu.wfust.learning.platform.mapper.CourseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeCoverageAuditService {

    static final String RULE_DESCRIPTION =
        "按 V18 课程、章节、知识点、启用练习题、启用干扰项错因诊断、已发布微课与概念闪卡关联状态执行只读 SQL 聚合与透明规则检查（不生成黑盒质量评分）。";

    private final CourseMapper courses;
    private final AdminContentAuditMapper auditMapper;

    public KnowledgeCoverageAuditService(CourseMapper courses, AdminContentAuditMapper auditMapper) {
        this.courses = courses;
        this.auditMapper = auditMapper;
    }

    public CoverageAuditReport audit(Long courseId) {
        String courseName = "全部课程";
        if (courseId != null) {
            CourseEntity course = courses.selectById(courseId);
            if (course == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在");
            }
            courseName = course.getName();
        }

        List<AdminContentAuditMapper.AuditRow> rows = auditMapper.findCoverageRows(courseId);
        List<CoverageAuditItem> items = new ArrayList<>(rows.size());

        int fullyCoveredPoints = 0;
        int missingQuestionsCount = 0;
        int missingDiagnosisCount = 0;
        int missingMicroLessonCount = 0;
        int missingFlashcardCount = 0;
        int coveredQuestionsTotal = 0;
        int coveredDiagnosisTotal = 0;
        int publishedMicroLessonsTotal = 0;
        int flashcardReviewRecordsTotal = 0;

        for (AdminContentAuditMapper.AuditRow row : rows) {
            int sectionCount = toInt(row.sectionCount());
            int questionCount = toInt(row.questionCount());
            int diagnosisCount = toInt(row.diagnosisCount());
            int publishedMicroLessonCount = toInt(row.publishedMicroLessonCount());
            int draftMicroLessonCount = toInt(row.draftMicroLessonCount());
            int flashcardReviewCount = toInt(row.flashcardReviewCount());
            boolean hasDescription = row.description() != null && !row.description().isBlank();
            boolean flashcardReady = sectionCount > 0 && hasDescription;

            List<GapTag> gaps = new ArrayList<>();
            if (questionCount == 0) {
                gaps.add(new GapTag("NO_QUESTIONS", "无练习题", "补录题库", "question-bank"));
                missingQuestionsCount++;
            }
            if (diagnosisCount == 0) {
                gaps.add(new GapTag("NO_DIAGNOSIS", "缺错因诊断", "配置错因", "diagnosis"));
                missingDiagnosisCount++;
            }
            if (publishedMicroLessonCount == 0) {
                gaps.add(new GapTag("NO_MICRO_LESSON", "无已发布微课", "管理微课", "micro-lessons"));
                missingMicroLessonCount++;
            }
            if (!flashcardReady) {
                gaps.add(new GapTag("NO_FLASHCARD", "无关联闪卡", "补全知识点", "course"));
                missingFlashcardCount++;
            }
            if (gaps.isEmpty()) {
                fullyCoveredPoints++;
            }

            coveredQuestionsTotal += questionCount;
            coveredDiagnosisTotal += diagnosisCount;
            publishedMicroLessonsTotal += publishedMicroLessonCount;
            flashcardReviewRecordsTotal += flashcardReviewCount;

            items.add(new CoverageAuditItem(
                row.knowledgePointId(),
                row.courseId(),
                row.courseName(),
                row.chapterId(),
                row.chapterName(),
                row.knowledgePointName(),
                row.graphEntity(),
                Boolean.TRUE.equals(row.enabled()),
                sectionCount,
                hasDescription,
                questionCount,
                diagnosisCount,
                publishedMicroLessonCount,
                draftMicroLessonCount,
                flashcardReviewCount,
                flashcardReady,
                List.copyOf(gaps)
            ));
        }

        CoverageSummary summary = new CoverageSummary(
            items.size(),
            fullyCoveredPoints,
            missingQuestionsCount,
            missingDiagnosisCount,
            missingMicroLessonCount,
            missingFlashcardCount,
            coveredQuestionsTotal,
            coveredDiagnosisTotal,
            publishedMicroLessonsTotal,
            flashcardReviewRecordsTotal
        );

        return new CoverageAuditReport(courseId, courseName, RULE_DESCRIPTION, summary, List.copyOf(items));
    }

    private static int toInt(Long value) {
        if (value == null || value <= 0L) return 0;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    public record GapTag(String code, String label, String actionLabel, String targetTab) { }

    public record CoverageSummary(
        int totalPoints,
        int fullyCoveredPoints,
        int missingQuestionsCount,
        int missingDiagnosisCount,
        int missingMicroLessonCount,
        int missingFlashcardCount,
        int coveredQuestionsTotal,
        int coveredDiagnosisTotal,
        int publishedMicroLessonsTotal,
        int flashcardReviewRecordsTotal
    ) { }

    public record CoverageAuditItem(
        Long knowledgePointId,
        Long courseId,
        String courseName,
        Long chapterId,
        String chapterName,
        String knowledgePointName,
        String graphEntity,
        boolean enabled,
        int sectionCount,
        boolean hasDescription,
        int questionCount,
        int diagnosisCount,
        int publishedMicroLessonCount,
        int draftMicroLessonCount,
        int flashcardReviewCount,
        boolean flashcardReady,
        List<GapTag> gaps
    ) { }

    public record CoverageAuditReport(
        Long courseId,
        String courseName,
        String ruleDescription,
        CoverageSummary summary,
        List<CoverageAuditItem> items
    ) { }
}
