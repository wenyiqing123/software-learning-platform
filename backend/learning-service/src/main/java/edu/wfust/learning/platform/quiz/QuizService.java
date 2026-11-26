package edu.wfust.learning.platform.quiz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import edu.wfust.learning.platform.entity.KnowledgePointEntity;
import edu.wfust.learning.platform.entity.QuizAttemptEntity;
import edu.wfust.learning.platform.entity.QuizQuestionEntity;
import edu.wfust.learning.platform.entity.WrongQuestionEntity;
import edu.wfust.learning.platform.mapper.KnowledgePointMapper;
import edu.wfust.learning.platform.mapper.QuizAttemptMapper;
import edu.wfust.learning.platform.mapper.QuizQuestionMapper;
import edu.wfust.learning.platform.mapper.WrongQuestionMapper;
import edu.wfust.learning.platform.messaging.LearningEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class QuizService {
    private final QuizQuestionMapper questionMapper;
    private final QuizAttemptMapper attemptMapper;
    private final WrongQuestionMapper wrongMapper;
    private final KnowledgePointMapper knowledgeMapper;
    private final ObjectMapper objectMapper;
    private final QuizGrader grader;
    private final LearningEventPublisher eventPublisher;

    public QuizService(QuizQuestionMapper questionMapper, QuizAttemptMapper attemptMapper, WrongQuestionMapper wrongMapper,
                       KnowledgePointMapper knowledgeMapper, ObjectMapper objectMapper, LearningEventPublisher eventPublisher) {
        this.questionMapper = questionMapper;
        this.attemptMapper = attemptMapper;
        this.wrongMapper = wrongMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.objectMapper = objectMapper;
        this.grader = new QuizGrader();
        this.eventPublisher = eventPublisher;
    }

    public List<QuestionView> questions(Long knowledgePointId) {
        QueryWrapper<QuizQuestionEntity> query = new QueryWrapper<QuizQuestionEntity>().eq("enabled", true)
                .inSql("course_id", "SELECT id FROM course WHERE enabled = 1").orderByAsc("id");
        if (knowledgePointId != null) query.eq("knowledge_point_id", knowledgePointId);
        return questionMapper.selectList(query).stream().map(this::toView).toList();
    }

    @Transactional
    public SubmitAnswerResponse submit(Long userId, Long questionId, List<String> submitted) {
        return submit(userId, questionId, submitted, true);
    }

    @Transactional
    public SubmitAnswerResponse submitForSpacedReview(Long userId, Long questionId, List<String> submitted) {
        return submit(userId, questionId, submitted, false);
    }

    private SubmitAnswerResponse submit(Long userId, Long questionId, List<String> submitted, boolean autoMasterCorrectAnswer) {
        if (submitted == null || submitted.isEmpty() || submitted.size() > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效答案");
        }
        QuizQuestionEntity question = questionMapper.selectOne(new QueryWrapper<QuizQuestionEntity>()
                .eq("id", questionId).eq("enabled", true)
                .inSql("course_id", "SELECT id FROM course WHERE enabled = 1").last("LIMIT 1"));
        if (question == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在或已停用");
        }
        var optionKeys = readOptions(question.getOptionsJson()).stream().map(OptionView::key).collect(java.util.stream.Collectors.toSet());
        boolean invalid = submitted.stream().anyMatch(value -> value == null || !optionKeys.contains(value))
                || new java.util.HashSet<>(submitted).size() != submitted.size()
                || (!"MULTIPLE".equals(question.getQuestionType()) && submitted.size() != 1);
        if (invalid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择本题提供的有效选项");
        List<String> expected = readAnswers(question.getAnswerJson());
        boolean correct = grader.isCorrect(submitted, expected);
        QuizAttemptEntity attempt = new QuizAttemptEntity();
        attempt.setUserId(userId);
        attempt.setQuestionId(questionId);
        attempt.setSubmittedAnswerJson(writeJson(submitted));
        attempt.setIsCorrect(correct);
        attemptMapper.insert(attempt);

        if (correct) {
            if (autoMasterCorrectAnswer) markExistingWrongMastered(userId, questionId);
        } else {
            upsertWrongQuestion(userId, questionId);
        }

        eventPublisher.publishAfterCommit(userId, question.getKnowledgePointId(), correct, attempt.getId());
        return new SubmitAnswerResponse(attempt.getId(), questionId, correct, expected, question.getExplanation(), attemptMapper.countForUser(userId));
    }

    public List<WrongQuestionView> wrongQuestions(Long userId) {
        return wrongQuestions(userId, false);
    }

    public List<WrongQuestionView> wrongQuestions(Long userId, boolean includeMastered) {
        QueryWrapper<WrongQuestionEntity> query = new QueryWrapper<WrongQuestionEntity>().eq("user_id", userId);
        if (!includeMastered) query.ne("status", "MASTERED");
        query.orderByDesc("wrong_count").orderByDesc("last_wrong_at");
        List<WrongQuestionEntity> wrongItems = wrongMapper.selectList(query);
        if (includeMastered) {
            wrongItems = wrongItems.stream()
                    .sorted(java.util.Comparator.comparingInt(item -> "MASTERED".equals(item.getStatus()) ? 1 : 0))
                    .toList();
        }
        return wrongItems.stream().map(item -> {
            QuizQuestionEntity question = questionMapper.selectById(item.getQuestionId());
            KnowledgePointEntity point = question == null || question.getKnowledgePointId() == null
                    ? null : knowledgeMapper.selectById(question.getKnowledgePointId());
            return new WrongQuestionView(item.getId(), item.getQuestionId(), question == null ? "题目已删除" : question.getStem(),
                    point == null ? "软件工程基础" : point.getName(), item.getWrongCount(), item.getStatus());
        }).toList();
    }

    @Transactional
    public void markMastered(Long userId, Long wrongItemId) {
        WrongQuestionEntity wrong = wrongMapper.selectOne(new QueryWrapper<WrongQuestionEntity>()
                .eq("id", wrongItemId).eq("user_id", userId).last("LIMIT 1"));
        if (wrong == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "错题不存在");
        wrongMapper.update(null, new UpdateWrapper<WrongQuestionEntity>().eq("id", wrongItemId).eq("user_id", userId)
                .set("status", "MASTERED").setSql("mastered_at = CURRENT_TIMESTAMP(6)"));
    }

    @Transactional
    public SubmitAnswerResponse review(Long userId, Long wrongItemId, List<String> submitted) {
        WrongQuestionEntity wrong = wrongMapper.selectOne(new QueryWrapper<WrongQuestionEntity>()
                .eq("id", wrongItemId).eq("user_id", userId).ne("status", "MASTERED").last("LIMIT 1"));
        if (wrong == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "错题不存在或已掌握");
        return submit(userId, wrong.getQuestionId(), submitted);
    }

    private void upsertWrongQuestion(Long userId, Long questionId) {
        WrongQuestionEntity existing = wrongMapper.selectOne(new QueryWrapper<WrongQuestionEntity>()
                .eq("user_id", userId).eq("question_id", questionId).last("LIMIT 1"));
        if (existing == null) {
            WrongQuestionEntity wrong = new WrongQuestionEntity();
            wrong.setUserId(userId);
            wrong.setQuestionId(questionId);
            wrong.setWrongCount(1);
            wrong.setStatus("UNMASTERED");
            wrongMapper.insert(wrong);
        } else {
            wrongMapper.update(null, new UpdateWrapper<WrongQuestionEntity>().eq("id", existing.getId())
                    .setSql("wrong_count = wrong_count + 1").set("status", "UNMASTERED")
                    .set("mastered_at", null).setSql("last_wrong_at = CURRENT_TIMESTAMP(6)"));
        }
    }

    private void markExistingWrongMastered(Long userId, Long questionId) {
        wrongMapper.update(null, new UpdateWrapper<WrongQuestionEntity>().eq("user_id", userId).eq("question_id", questionId)
                .set("status", "MASTERED").setSql("mastered_at = CURRENT_TIMESTAMP(6)"));
    }

    private QuestionView toView(QuizQuestionEntity question) {
        KnowledgePointEntity point = question.getKnowledgePointId() == null ? null : knowledgeMapper.selectById(question.getKnowledgePointId());
        return new QuestionView(question.getId(), question.getQuestionType(), question.getStem(), readOptions(question.getOptionsJson()),
                point == null ? null : point.getName(), question.getDifficulty());
    }

    private List<OptionView> readOptions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalStateException("题目选项数据格式错误", exception); }
    }

    private List<String> readAnswers(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() { }); }
        catch (Exception exception) { throw new IllegalStateException("题目答案数据格式错误", exception); }
    }

    private String writeJson(List<String> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("无法保存答题记录", exception); }
    }

    public record OptionView(String key, String text) { }
    public record QuestionView(Long id, String type, String stem, List<OptionView> options, String knowledgePoint, Integer difficulty) { }
    public record SubmitAnswerResponse(Long attemptId, Long questionId, boolean correct, List<String> correctAnswer, String explanation, long totalAttempts) { }
    public record WrongQuestionView(Long id, Long questionId, String question, String knowledgePoint, Integer wrongCount, String status) { }
}
