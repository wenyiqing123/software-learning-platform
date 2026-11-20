package edu.wfust.learning.platform.experiment;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ExperimentMetricsTest {
    @Test void identicalChineseAnswerScoresOneAndDetectsExpectedEntities() {
        var m = new ExperimentMetrics().evaluate("需求规格说明书", "需求规格说明书", List.of("需求", "说明书"));
        assertThat(m.rougeL()).isEqualTo(1.0); assertThat(m.bleu4()).isEqualTo(1.0); assertThat(m.entityRecall()).isEqualTo(1.0);
    }
    @Test void disjointAnswersScoreZeroAndMissingReferenceHasNoInventedScore() {
        var evaluator = new ExperimentMetrics();
        assertThat(evaluator.evaluate("软件测试", "需求分析", List.of("需求")).rougeL()).isZero();
        var missing = evaluator.evaluate("某个回答", "", List.of());
        assertThat(missing.rougeL()).isNull(); assertThat(missing.bleu4()).isNull(); assertThat(missing.entityRecall()).isNull();
    }
    @Test void entityRecallAndLatencyPercentileUseObservedData() {
        var m = new ExperimentMetrics().evaluate("需求分析使用数据流图", "需求分析使用数据流图与数据字典", List.of("数据流图", "数据字典"));
        assertThat(m.entityRecall()).isEqualTo(0.5); assertThat(m.rougeL()).isBetween(0.0,1.0);
        assertThat(ExperimentMetrics.percentile95(List.of(50L,10L,100L,30L,20L))).isEqualTo(100L);
    }
    @Test void reportsEffectiveBleuOrderForShortAnswers() {
        var scores = new ExperimentMetrics().evaluate("需", "需求", List.of());
        assertThat(scores.bleuOrder()).isEqualTo(1);
    }
}
