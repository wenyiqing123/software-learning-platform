package edu.wfust.learning.platform.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.knowledge.MasteryRetentionPolicy;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.QuizAttemptMapper;
import edu.wfust.learning.platform.mapper.WrongQuestionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('STUDENT')")
public class DashboardController {
    private final QuizAttemptMapper attempts;
    private final WrongQuestionMapper wrongQuestions;
    private final KnowledgeMasteryMapper mastery;
    private final Clock clock;
    private final MasteryRetentionPolicy retentionPolicy = new MasteryRetentionPolicy();

    public DashboardController(QuizAttemptMapper attempts, WrongQuestionMapper wrongQuestions, KnowledgeMasteryMapper mastery) {
        this(attempts, wrongQuestions, mastery, MasteryRetentionPolicy.DEFAULT_CLOCK);
    }

    @Autowired
    public DashboardController(QuizAttemptMapper attempts, WrongQuestionMapper wrongQuestions,
                               KnowledgeMasteryMapper mastery, Clock clock) {
        this.attempts = attempts;
        this.wrongQuestions = wrongQuestions;
        this.mastery = mastery;
        this.clock = clock == null ? MasteryRetentionPolicy.DEFAULT_CLOCK : clock;
    }

    @GetMapping("/overview")
    public Overview overview(Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        long total = attempts.countForUser(userId);
        long correct = attempts.countCorrectForUser(userId);
        List<DailyActivity> activity = attempts.activityLast7Days(userId).stream()
                .map(row -> new DailyActivity(String.valueOf(row.get("day")), ((Number) row.get("attempts")).longValue())).toList();

        long rawMasteryAvg = Math.round(mastery.averageForUser(userId));
        double peakMasteryAvg = rawMasteryAvg;
        double effectiveMasteryAvg = rawMasteryAvg;
        long daysSinceLastPractice = 0L;
        String reviewUrgency = MasteryRetentionPolicy.ReviewUrgency.FRESH.name();

        List<KnowledgeMasteryEntity> rows = mastery.selectList(
                new QueryWrapper<KnowledgeMasteryEntity>().eq("user_id", userId));
        if (rows != null && !rows.isEmpty()) {
            double peakSum = 0.0;
            double effectiveSum = 0.0;
            long minDays = Long.MAX_VALUE;
            int highestRank = 0;
            for (KnowledgeMasteryEntity row : rows) {
                MasteryRetentionPolicy.RetentionSnapshot snapshot = retentionPolicy.evaluate(row, clock);
                peakSum += snapshot.peakMastery();
                effectiveSum += snapshot.effectiveMastery();
                if (row.getLastStudiedAt() != null) {
                    minDays = Math.min(minDays, snapshot.daysSinceLastPractice());
                }
                int rank = switch (snapshot.reviewUrgency()) {
                    case DUE -> 2;
                    case FADING -> 1;
                    case FRESH -> 0;
                };
                highestRank = Math.max(highestRank, rank);
            }
            peakMasteryAvg = MasteryRetentionPolicy.round2(peakSum / rows.size());
            effectiveMasteryAvg = MasteryRetentionPolicy.round2(effectiveSum / rows.size());
            daysSinceLastPractice = minDays == Long.MAX_VALUE ? 0L : minDays;
            reviewUrgency = highestRank == 2 ? MasteryRetentionPolicy.ReviewUrgency.DUE.name()
                    : highestRank == 1 ? MasteryRetentionPolicy.ReviewUrgency.FADING.name()
                    : MasteryRetentionPolicy.ReviewUrgency.FRESH.name();
        }

        return new Overview(rawMasteryAvg, total, wrongQuestions.countOpenForUser(userId),
                total == 0 ? 0 : Math.round(correct * 100.0 / total), activity,
                peakMasteryAvg, effectiveMasteryAvg, daysSinceLastPractice, reviewUrgency);
    }

    public record DailyActivity(String day, long attempts) { }

    public record Overview(long mastery, long completed, long wrongCount, long accuracy, List<DailyActivity> activity,
                           Double peakMastery, Double effectiveMastery, Long daysSinceLastPractice,
                           String reviewUrgency) {
        public Overview(long mastery, long completed, long wrongCount, long accuracy, List<DailyActivity> activity) {
            this(mastery, completed, wrongCount, accuracy, activity, (double) mastery, (double) mastery, 0L, "FRESH");
        }
    }
}
