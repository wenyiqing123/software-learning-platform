package edu.wfust.learning.ai;

import org.springframework.stereotype.Component;

import java.util.List;

/** Explicit mode-to-strategy dispatch; unsupported modes never silently fall back. */
@Component
public final class QaStrategySelector {
    private final List<QaExecutionStrategy> strategies;

    public QaStrategySelector() {
        this(List.of(new ChatQaStrategy(), new GroundedRankedQaStrategy()));
    }

    QaStrategySelector(List<QaExecutionStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public static QaStrategySelector defaultSelector() { return new QaStrategySelector(); }

    public QaExecutionStrategy select(QaMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        return select(mode, mode == QaMode.RANKED);
    }

    public QaExecutionStrategy select(QaMode mode, boolean groundedRanked) {
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        if (groundedRanked && mode == QaMode.RANKED) {
            return strategies.stream().filter(strategy -> strategy.family() == QaExecutionStrategy.Family.GROUNDED_RANKED)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("No grounded RANKED strategy"));
        }
        return strategies.stream().filter(strategy -> strategy.family() == QaExecutionStrategy.Family.CHAT
                        && strategy.supports(mode)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No QA strategy for mode " + mode));
    }
}
