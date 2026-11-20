package edu.wfust.learning.platform.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class EvaluationStatistics {
    private EvaluationStatistics() { }
    public record Row(Long userId, String conditionKey, String taskKey, String taskOrder, Integer elapsedSeconds, Integer effort, Double preScore, Double postScore, Double delayedScore) { }
    public record Summary(String conditionKey, int sampleCount, Double meanPreScore, Double medianPreScore, Double stddevPreScore, Double iqrPreScore,
                          Double meanPostScore, Double medianPostScore, Double stddevPostScore, Double iqrPostScore,
                          Double meanDelayedScore, Double medianDelayedScore, Double stddevDelayedScore, Double iqrDelayedScore,
                          Double meanGain, Double meanEffort, Double medianEffort, Double stddevEffort, Double iqrEffort,
                          Double meanElapsedSeconds, Double medianElapsedSeconds, Double stddevElapsedSeconds, Double iqrElapsedSeconds) { }

    public static Summary summarize(String conditionKey, List<Row> rows) {
        List<Row> filtered = rows.stream().filter(row -> conditionKey == null || conditionKey.equals(row.conditionKey())).toList();
        var pre = values(filtered, Row::preScore); var post = values(filtered, Row::postScore); var delayed = values(filtered, Row::delayedScore); var effort = values(filtered, row -> row.effort() == null ? null : row.effort().doubleValue()); var elapsed = values(filtered, row -> row.elapsedSeconds() == null ? null : row.elapsedSeconds().doubleValue());
        Double gain = mean(filtered.stream().filter(row -> row.preScore() != null && row.postScore() != null).map(row -> row.postScore() - row.preScore()).toList());
        return new Summary(conditionKey, filtered.size(), mean(pre), median(pre), stddev(pre), iqr(pre), mean(post), median(post), stddev(post), iqr(post), mean(delayed), median(delayed), stddev(delayed), iqr(delayed), gain, mean(effort), median(effort), stddev(effort), iqr(effort), mean(elapsed), median(elapsed), stddev(elapsed), iqr(elapsed));
    }

    public static String participantCode(long userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Long.toString(userId).getBytes(StandardCharsets.UTF_8));
            return "P-" + HexFormat.of().formatHex(digest).substring(0, 10).toUpperCase(Locale.ROOT);
        } catch (Exception exception) { throw new IllegalStateException("无法生成匿名参与者编号", exception); }
    }
    private static List<Double> values(List<Row> rows, java.util.function.Function<Row, Double> getter) { return rows.stream().map(getter).filter(Objects::nonNull).sorted().toList(); }
    private static Double mean(List<Double> values) { return values.isEmpty() ? null : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0)); }
    private static Double median(List<Double> values) { if (values.isEmpty()) return null; int n=values.size(); return round(n%2==1?values.get(n/2):(values.get(n/2-1)+values.get(n/2))/2); }
    private static Double stddev(List<Double> values) { if (values.isEmpty()) return null; double mean=values.stream().mapToDouble(Double::doubleValue).average().orElse(0); return round(Math.sqrt(values.stream().mapToDouble(value -> Math.pow(value-mean,2)).average().orElse(0))); }
    private static Double iqr(List<Double> values) { if(values.isEmpty()) return null; return round(percentile(values,.75)-percentile(values,.25)); }
    private static double percentile(List<Double> values,double p){if(values.size()==1)return values.getFirst();double index=(values.size()-1)*p;int lower=(int)Math.floor(index),upper=(int)Math.ceil(index);if(lower==upper)return values.get(lower);return values.get(lower)+(values.get(upper)-values.get(lower))*(index-lower);}
    private static double round(double value){return Math.round(value*100.0)/100.0;}
}
