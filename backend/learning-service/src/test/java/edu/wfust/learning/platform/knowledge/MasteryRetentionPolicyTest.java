package edu.wfust.learning.platform.knowledge;

import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class MasteryRetentionPolicyTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZONE);
    private final MasteryRetentionPolicy policy = new MasteryRetentionPolicy();

    @Test
    void doesNotDecayWithinOneDayGraceWindow() {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        var sameDay = policy.evaluate(80.0, 90.0, now.minusHours(6), FIXED_CLOCK);
        assertThat(sameDay.currentMastery()).isEqualTo(80.0);
        assertThat(sameDay.peakMastery()).isEqualTo(90.0);
        assertThat(sameDay.effectiveMastery()).isEqualTo(80.0);
        assertThat(sameDay.daysSinceLastPractice()).isZero();
        assertThat(sameDay.reviewUrgency()).isEqualTo(MasteryRetentionPolicy.ReviewUrgency.FRESH);

        var exactlyOneDay = policy.evaluate(80.0, 80.0, now.minusDays(1), FIXED_CLOCK);
        assertThat(exactlyOneDay.daysSinceLastPractice()).isEqualTo(1L);
        assertThat(exactlyOneDay.effectiveMastery()).isEqualTo(80.0);
        assertThat(exactlyOneDay.reviewUrgency()).isEqualTo(MasteryRetentionPolicy.ReviewUrgency.FRESH);
    }

    @Test
    void decaysDeterministicallyByFourteenDayHalfLifeAndClassifiesUrgency() {
        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);

        // 7 天未复习：80 * 0.5^(7/14) ≈ 56.57，跌破 60 阈值进入 FADING
        var sevenDays = policy.evaluate(80.0, 80.0, now.minusDays(7), FIXED_CLOCK);
        assertThat(sevenDays.daysSinceLastPractice()).isEqualTo(7L);
        assertThat(sevenDays.peakMastery()).isEqualTo(80.0);
        assertThat(sevenDays.effectiveMastery()).isCloseTo(56.57, offset(0.05));
        assertThat(sevenDays.reviewUrgency()).isEqualTo(MasteryRetentionPolicy.ReviewUrgency.FADING);

        // 14 天未复习：精确半衰期 80 * 0.5 = 40.00，进入 DUE
        var fourteenDays = policy.evaluate(80.0, 85.0, now.minusDays(14), FIXED_CLOCK);
        assertThat(fourteenDays.daysSinceLastPractice()).isEqualTo(14L);
        assertThat(fourteenDays.peakMastery()).isEqualTo(85.0);
        assertThat(fourteenDays.effectiveMastery()).isEqualTo(40.0);
        assertThat(fourteenDays.reviewUrgency()).isEqualTo(MasteryRetentionPolicy.ReviewUrgency.DUE);
    }

    @Test
    void clampsBoundsHandlesLegacyNullPeakAndNeverDecaysFutureOrEmptyRows() {
        assertThat(policy.evaluate((KnowledgeMasteryEntity) null, FIXED_CLOCK))
                .isEqualTo(MasteryRetentionPolicy.RetentionSnapshot.EMPTY);

        LocalDateTime now = LocalDateTime.now(FIXED_CLOCK);
        // 旧行 peakMastery 为 null 时回退到 masteryScore，且合法区间截断在 [0, 100]
        var legacyRow = new KnowledgeMasteryEntity();
        legacyRow.setMasteryScore(new BigDecimal("120.00"));
        legacyRow.setLastStudiedAt(now.plusDays(2));
        var legacySnap = policy.evaluate(legacyRow, FIXED_CLOCK);
        assertThat(legacySnap.currentMastery()).isEqualTo(100.0);
        assertThat(legacySnap.peakMastery()).isEqualTo(100.0);
        assertThat(legacySnap.effectiveMastery()).isEqualTo(100.0);
        assertThat(legacySnap.daysSinceLastPractice()).isZero();
        assertThat(legacySnap.reviewUrgency()).isEqualTo(MasteryRetentionPolicy.ReviewUrgency.FRESH);

        // 极长间隔仍保持 >= 0 且不会变成 NaN/负数
        var stale = policy.evaluate(75.0, 90.0, now.minusDays(400), FIXED_CLOCK);
        assertThat(stale.effectiveMastery()).isBetween(0.0, 100.0);
        assertThat(stale.peakMastery()).isEqualTo(90.0);
        assertThat(stale.reviewUrgency()).isEqualTo(MasteryRetentionPolicy.ReviewUrgency.DUE);
    }

    @Test
    void monotonicPeakNeverDecreasesWhenCurrentMasteryDrops() {
        assertThat(MasteryRetentionPolicy.monotonicPeak(80.0, 65.0)).isEqualTo(80.0);
        assertThat(MasteryRetentionPolicy.monotonicPeak(80.0, 92.5)).isEqualTo(92.5);
        assertThat(MasteryRetentionPolicy.monotonicPeak(new BigDecimal("70.00"), new BigDecimal("50.00")))
                .isEqualByComparingTo("70.00");
        assertThat(MasteryRetentionPolicy.monotonicPeak(null, new BigDecimal("60.00")))
                .isEqualByComparingTo("60.00");
    }
}
