package edu.wfust.learning.platform.quiz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.entity.QuizOptionDiagnosisEntity;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.QuizDiagnosisMapper;
import edu.wfust.learning.platform.mapper.QuizOptionDiagnosisMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class QuizDiagnosisService {
    private final QuizQuestionMapper questions;
    private final KnowledgePointMapper points;
    private final QuizOptionDiagnosisMapper diagnoses;
    private final QuizDiagnosisMapper query;
    private final ObjectMapper json;

    public QuizDiagnosisService(QuizQuestionMapper questions, KnowledgePointMapper points,
                                QuizOptionDiagnosisMapper diagnoses, QuizDiagnosisMapper query, ObjectMapper json) {
        this.questions = questions;
        this.points = points;
        this.diagnoses = diagnoses;
        this.query = query;
        this.json = json;
    }

    public List<DiagnosisView> diagnose(Long questionId, List<String> selected) {
        QuizQuestionEntity question = question(questionId);
        if (!Boolean.TRUE.equals(question.getEnabled())) throw notFound();
        Set<String> correct = new HashSet<>(answers(question.getAnswerJson()));
        List<String> keys = selected == null ? List.of() : selected.stream().filter(key -> key != null && !key.isBlank())
            .map(this::key).distinct().filter(key -> !correct.contains(key)).toList();
        if (keys.isEmpty()) return List.of();
        Map<String, String> optionText = options(question.getOptionsJson());
        return query.matching(questionId, keys).stream()
            .filter(row -> !correct.contains(key(row.getOptionKey())))
            .map(row -> new DiagnosisView(key(row.getOptionKey()), optionText.getOrDefault(key(row.getOptionKey()), ""),
                row.getKnowledgePointId(), row.getKnowledgePointName(), row.getRationale(), row.getSectionId(),
                row.getSectionTitle(), row.getPracticeQuestionId())).toList();
    }

    public List<ConfiguredView> configured(Long questionId) {
        QuizQuestionEntity question = question(questionId);
        Map<String, String> optionText = options(question.getOptionsJson());
        return diagnoses.selectList(new QueryWrapper<QuizOptionDiagnosisEntity>().eq("question_id", questionId).orderByAsc("option_key"))
            .stream().map(row -> {
                KnowledgePointEntity point = points.selectById(row.getKnowledgePointId());
                return new ConfiguredView(row.getId(), key(row.getOptionKey()), optionText.getOrDefault(key(row.getOptionKey()), ""),
                    row.getKnowledgePointId(), point == null ? "" : point.getName(), row.getRationale(), row.getEnabled());
            }).toList();
    }

    @Transactional
    public List<ConfiguredView> replace(Long questionId, List<ConfigEntry> entries) {
        QuizQuestionEntity question = question(questionId);
        Map<String, String> optionText = options(question.getOptionsJson());
        Set<String> correct = new HashSet<>(answers(question.getAnswerJson()));
        Set<String> seen = new HashSet<>();
        List<QuizOptionDiagnosisEntity> rows = entries == null ? List.of() : entries.stream().map(entry -> {
            String optionKey = key(entry.optionKey());
            if (!optionText.containsKey(optionKey)) throw bad("错因关联的选项不属于该题");
            if (correct.contains(optionKey)) throw bad("正确答案不能配置为干扰项错因");
            if (!seen.add(optionKey)) throw bad("同一干扰项只能配置一个混淆知识点");
            KnowledgePointEntity point = entry.knowledgePointId() == null ? null : points.selectById(entry.knowledgePointId());
            if (point == null || !Boolean.TRUE.equals(point.getEnabled()) || !question.getCourseId().equals(point.getCourseId()))
                throw bad("混淆知识点必须属于该题所在课程且处于启用状态");
            if (entry.rationale() == null || entry.rationale().isBlank()) throw bad("请填写错因匹配依据");
            QuizOptionDiagnosisEntity row = new QuizOptionDiagnosisEntity();
            row.setQuestionId(questionId); row.setOptionKey(optionKey); row.setKnowledgePointId(point.getId());
            row.setRationale(entry.rationale().trim()); row.setEnabled(true); return row;
        }).toList();
        diagnoses.delete(new QueryWrapper<QuizOptionDiagnosisEntity>().eq("question_id", questionId));
        rows.forEach(diagnoses::insert);
        return configured(questionId);
    }

    private QuizQuestionEntity question(Long id) {
        QuizQuestionEntity row = id == null ? null : questions.selectById(id);
        if (row == null) throw notFound();
        return row;
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在或已停用"); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private String key(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private List<String> answers(String value) { return read(value, new TypeReference<>() { }); }
    private Map<String, String> options(String value) {
        List<Option> rows = read(value, new TypeReference<>() { });
        Map<String, String> result = new HashMap<>();
        rows.forEach(row -> result.put(key(row.key()), row.text()));
        return result;
    }
    private <T> T read(String value, TypeReference<T> type) {
        try { return json.readValue(value == null ? "[]" : value, type); }
        catch (Exception e) { throw new IllegalStateException("题目配置数据格式错误", e); }
    }

    private record Option(String key, String text) { }
    public record ConfigEntry(String optionKey, Long knowledgePointId, String rationale) { }
    public record ConfiguredView(Long id, String optionKey, String optionText, Long knowledgePointId,
                                 String knowledgePointName, String rationale, Boolean enabled) { }
    public record DiagnosisView(String optionKey, String optionText, Long knowledgePointId, String knowledgePointName,
                                String rationale, Long sectionId, String sectionTitle, Long practiceQuestionId) { }
}
