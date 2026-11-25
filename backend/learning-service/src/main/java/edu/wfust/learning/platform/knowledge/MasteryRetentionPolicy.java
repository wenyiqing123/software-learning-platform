package edu.wfust.learning.platform.knowledge;

import edu.wfust.learning.platform.entity.KnowledgeMasteryEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 基于距上次有效学习天数的可解释遗忘衰减规则（确定性半衰期规则，非机器学习预测）。
 * <ul>
 *   <li>距上次有效学习 {@code <= 1} 天不衰减；超过 1 天按常数半衰期 {@code HALF_LIFE_DAYS = 14} 计算当前记忆保持估值 {@code effectiveMastery}。</li>
 *   <li>单调维护历史峰值 {@code peakMastery = max(existingPeak, currentMastery)}；先修解锁门槛严格以历史峰值为准，已解锁能力不回退锁死。</li>
 * </ul>
 */
public final class MasteryRetentionPolicy {
    public static final double HALF_LIFE_DAYS = 14.0;
    public static final long GRACE_WINDOW_DAYS = 1L;
    public static final long FADING_DAYS_THRESHOLD = 5L;
    public static final long DUE_DAYS_THRESHOLD = 14L;
    public static final double MASTERED_THRESHOLD = 60.0;
    public static final double AT_RISK_RETENTION_FLOOR = 50.0;
    public static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Shanghai"));

    public enum ReviewUrgency {
        FRESH,
        FADING,
        DUE
    }

    public record RetentionSnapshot(
            double currentMastery,
            double peakMastery,
            double effectiveMastery,
            long daysSinceLastPractice,
            ReviewUrgency reviewUrgency
    ) {
        public static final RetentionSnapshot EMPTY = new RetentionSnapshot(0.0, 0.0, 0.0, 0L, ReviewUrgency.FRESH);
    }

    public RetentionSnapshot evaluate(KnowledgeMasteryEntity entity, Clock clock) {
        if (entity == null) {
            return RetentionSnapshot.EMPTY;
        }
        Double current = entity.getMasteryScore() == null ? 0.0 : entity.getMasteryScore().doubleValue();
        Double peak = entity.getPeakMastery() == null ? null : entity.getPeakMastery().doubleValue();
        return evaluate(current, peak, entity.getLastStudiedAt(), clock);
    }

    public RetentionSnapshot evaluate(Double masteryScore, Double peakMastery, LocalDateTime lastPracticedAt, Clock clock) {
        double current = clampScore(masteryScore);
        double peak = peakMastery == null ? current : Math.max(current, clampScore(peakMastery));
        if (lastPracticedAt == null) {
            return new RetentionSnapshot(round2(current), round2(peak), round2(current), 0L, ReviewUrgency.FRESH);
        }

        Clock effectiveClock = clock == null ? DEFAULT_CLOCK : clock;
        Duration elapsed = Duration.between(lastPracticedAt, LocalDateTime.now(effectiveClock));
        long days = elapsed.isNegative() ? 0L : elapsed.toDays();

        double decayed = days <= GRACE_WINDOW_DAYS
                ? current
                : current * Math.pow(0.5, days / HALF_LIFE_DAYS);
        double effective = round2(clampScore(decayed));

        ReviewUrgency urgency = classifyUrgency(current, peak, effective, days);
        return new RetentionSnapshot(round2(current), round2(peak), effective, days, urgency);
    }

    public static double monotonicPeak(Double existingPeak, Double newMastery) {
        return round2(Math.max(clampScore(existingPeak), clampScore(newMastery)));
    }

    public static BigDecimal monotonicPeak(BigDecimal existingPeak, BigDecimal newMastery) {
        double peak = monotonicPeak(
                existingPeak == null ? null : existingPeak.doubleValue(),
                newMastery == null ? null : newMastery.doubleValue()
        );
        return BigDecimal.valueOf(peak).setScale(2, RoundingMode.HALF_UP);
    }

    private static ReviewUrgency classifyUrgency(double current, double peak, double effective, long days) {
        if ((current <= 0.0 && peak <= 0.0) || days <= GRACE_WINDOW_DAYS) {
            return ReviewUrgency.FRESH;
        }
        if (days >= DUE_DAYS_THRESHOLD || (peak >= MASTERED_THRESHOLD && effective < AT_RISK_RETENTION_FLOOR)) {
            return ReviewUrgency.DUE;
        }
        if (days >= FADING_DAYS_THRESHOLD || (peak >= MASTERED_THRESHOLD && effective < MASTERED_THRESHOLD)) {
            return ReviewUrgency.FADING;
        }
        return ReviewUrgency.FRESH;
    }

    private static double clampScore(Double value) {
        if (value == null || !Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
