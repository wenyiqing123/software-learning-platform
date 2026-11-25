package edu.wfust.learning.platform.dashboard;

import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import edu.wfust.learning.platform.mapper.KnowledgeMasteryMapper;
import edu.wfust.learning.platform.mapper.QuizAttemptMapper;
import edu.wfust.learning.platform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void returnsDualValueRetentionSummaryAlongsideLegacyOverviewMetrics() {
        var attempts = mock(QuizAttemptMapper.class);
        var wrong = mock(WrongQuestionMapper.class);
        var mastery = mock(KnowledgeMasteryMapper.class);
        when(attempts.countForUser(7L)).thenReturn(10L);
        when(attempts.countCorrectForUser(7L)).thenReturn(8L);
        when(attempts.activityLast7Days(7L)).thenReturn(List.of());
        when(wrong.countOpenForUser(7L)).thenReturn(2L);
        when(mastery.averageForUser(7L)).thenReturn(80.0);

        var row1 = new KnowledgeMasteryEntity();
        row1.setKnowledgePointId(1L);
        row1.setMasteryScore(new BigDecimal("80.00"));
        row1.setPeakMastery(new BigDecimal("90.00"));
        row1.setLastStudiedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(14));

        var row2 = new KnowledgeMasteryEntity();
        row2.setKnowledgePointId(2L);
        row2.setMasteryScore(new BigDecimal("80.00"));
        row2.setPeakMastery(new BigDecimal("80.00"));
        row2.setLastStudiedAt(LocalDateTime.now(FIXED_CLOCK).minusDays(2));

        when(mastery.selectList(any())).thenReturn(List.of(row1, row2));

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("7");

        var controller = new DashboardController(attempts, wrong, mastery, FIXED_CLOCK);
        var overview = controller.overview(auth);

        assertThat(overview.mastery()).isEqualTo(80L);
        assertThat(overview.peakMastery()).isEqualTo(85.0);
        assertThat(overview.effectiveMastery()).isLessThan(80.0);
        assertThat(overview.daysSinceLastPractice()).isEqualTo(2L);
        assertThat(overview.reviewUrgency()).isEqualTo("DUE");
    }
}
