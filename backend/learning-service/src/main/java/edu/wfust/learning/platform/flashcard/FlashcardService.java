package edu.wfust.learning.platform.flashcard;

import edu.wfust.learning.platform.mapper.FlashcardMapper;
import edu.wfust.learning.platform.quiz.SpacedReviewPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashcardService {
    private final FlashcardMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    private final SpacedReviewPolicy policy = new SpacedReviewPolicy();

    public FlashcardService(FlashcardMapper mapper, ObjectMapper json, Clock clock) {
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
    }

    public List<FlashcardView> list(long userId, Long courseId, boolean dueOnly) {
        LocalDateTime now = LocalDateTime.now(clock);
        return mapper.findCards(userId, courseId, null, now).stream()
            .map(row -> toView(row, now))
            .filter(card -> !dueOnly || card.due())
            .toList();
    }

    @Transactional
    public FlashcardView review(long userId, long knowledgePointId, int recallRating) {
        if (recallRating < 0 || recallRating > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "回忆评分必须在 0 到 3 之间");
        }
        if (mapper.existsEnabledPoint(knowledgePointId) <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识点不存在或已停用");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 与 SpacedReviewPolicy 共用透明固定间隔：0=10分钟，1=1天，2=3天，3=7天
        int intervalMinutes = policy.nextIntervalMinutes(recallRating, recallRating > 0);
        LocalDateTime nextDueAt = now.plusMinutes(intervalMinutes);
        int updated = mapper.updateState(userId, knowledgePointId, recallRating, intervalMinutes, nextDueAt, now);
        if (updated == 0) {
            mapper.insertState(userId, knowledgePointId, recallRating, intervalMinutes, nextDueAt, now);
        }
        List<FlashcardMapper.FlashcardRow> rows = mapper.findCards(userId, null, knowledgePointId, now);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识点不存在或已停用");
        }
        return toView(rows.getFirst(), now);
    }

    private FlashcardView toView(FlashcardMapper.FlashcardRow row, LocalDateTime now) {
        boolean isDue = row.nextDueAt() == null || !row.nextDueAt().isAfter(now);
        double mastery = row.masteryScore() == null ? 0.0 : row.masteryScore().doubleValue();
        return new FlashcardView(
            row.knowledgePointId(),
            row.courseId(),
            row.courseName(),
            row.chapterName(),
            row.sectionId(),
            row.sectionTitle(),
            readObjectives(row.objectivesJson()),
            row.name(),
            row.graphEntity(),
            row.summary() == null ? "" : row.summary(),
            BigDecimal.valueOf(mastery).intValue(),
            row.recallRating(),
            row.reviewCount() == null ? 0 : row.reviewCount(),
            row.intervalMinutes(),
            isDue,
            row.nextDueAt(),
            row.lastReviewedAt()
        );
    }

    private List<String> readObjectives(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            List<String> parsed = json.readValue(raw, new TypeReference<>() {});
            return parsed == null ? List.of() : parsed;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public record FlashcardView(
        Long knowledgePointId,
        Long courseId,
        String courseName,
        String chapterName,
        Long sectionId,
        String sectionTitle,
        List<String> objectives,
        String name,
        String graphEntity,
        String summary,
        int masteryScore,
        Integer recallRating,
        int reviewCount,
        Integer intervalMinutes,
        boolean due,
        LocalDateTime nextDueAt,
        LocalDateTime lastReviewedAt
    ) { }
}
