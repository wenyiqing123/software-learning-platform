package edu.wfust.learning.platform.dashboard;

import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.QuizAttemptMapper;
import edu.wfust.learning.platform.mapper.WrongQuestionMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('STUDENT')")
public class DashboardController {
    private final QuizAttemptMapper attempts;
    private final WrongQuestionMapper wrongQuestions;
    private final KnowledgeMasteryMapper mastery;
    public DashboardController(QuizAttemptMapper attempts, WrongQuestionMapper wrongQuestions, KnowledgeMasteryMapper mastery) {
        this.attempts = attempts;
        this.wrongQuestions = wrongQuestions;
        this.mastery = mastery;
    }
    @GetMapping("/overview")
    public Overview overview(Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        long total = attempts.countForUser(userId);
        long correct = attempts.countCorrectForUser(userId);
        List<DailyActivity> activity = attempts.activityLast7Days(userId).stream()
                .map(row -> new DailyActivity(String.valueOf(row.get("day")), ((Number) row.get("attempts")).longValue())).toList();
        return new Overview(Math.round(mastery.averageForUser(userId)), total,
                wrongQuestions.countOpenForUser(userId), total == 0 ? 0 : Math.round(correct * 100.0 / total), activity);
    }
    public record DailyActivity(String day, long attempts) { }
    public record Overview(long mastery, long completed, long wrongCount, long accuracy, List<DailyActivity> activity) { }
}
