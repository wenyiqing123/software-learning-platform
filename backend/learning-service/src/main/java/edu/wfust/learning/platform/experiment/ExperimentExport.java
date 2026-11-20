package edu.wfust.learning.platform.experiment;

import org.springframework.stereotype.Component;
import java.util.*;

/** Server-side experiment export. Prompt bodies are intentionally excluded; only version/hash metadata is retained. */
@Component
public class ExperimentExport {
    private static final List<String> HEADERS = List.of("runId", "createdAt", "question", "experimentType", "resultKey", "mode", "modelVariant", "status", "latencyMs", "evidenceOrder", "promptVersion", "promptSha256", "systemPromptSha256", "model", "temperature", "maxTokens", "topK", "rougeL", "bleu4", "bleuOrder", "entityRecall", "manualRating", "answer", "error");

    public List<Map<String, Object>> rows(List<AiExperimentController.ExperimentView> runs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var run : runs) {
            Object metricsObject = run.evaluation().get("metricsByMode");
            Map<?, ?> metricsByMode = metricsObject instanceof Map<?, ?> map ? map : Map.of();
            String experimentType = text(run.evaluation().get("experimentType"));
            for (var raw : run.results()) {
                String key = text(raw.getOrDefault("resultKey", raw.get("mode")));
                Map<?, ?> metrics = metricsByMode.get(key) instanceof Map<?, ?> map ? map : Map.of();
                Map<?, ?> provenance = raw.get("provenance") instanceof Map<?, ?> map ? map : Map.of();
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("runId", run.id()); row.put("createdAt", run.createdAt()); row.put("question", run.question());
                row.put("experimentType", experimentType); row.put("resultKey", key); row.put("mode", text(raw.get("mode")));
                row.put("modelVariant", text(raw.get("modelVariant")));
                boolean failed = nonBlank(raw.get("error")); row.put("status", failed ? "FAILED" : "SUCCESS");
                row.put("latencyMs", raw.get("latencyMs")); row.put("evidenceOrder", evidenceOrder(raw.get("triples")));
                row.put("promptVersion", first(provenance, "systemPromptVersion", "promptTemplate"));
                row.put("promptSha256", provenance.get("promptSha256")); row.put("systemPromptSha256", provenance.get("systemPromptSha256"));
                row.put("model", provenance.get("model")); row.put("temperature", provenance.get("temperature"));
                row.put("maxTokens", provenance.get("maxTokens")); row.put("topK", provenance.get("topK"));
                row.put("rougeL", metrics.get("rougeL")); row.put("bleu4", metrics.get("bleu4")); row.put("bleuOrder", metrics.get("bleuOrder")); row.put("entityRecall", metrics.get("entityRecall"));
                row.put("manualRating", run.ratings().get(key)); row.put("answer", failed ? "" : raw.get("answer")); row.put("error", raw.get("error"));
                rows.add(row);
            }
        }
        return rows;
    }

    public static String csv(List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF").append(String.join(",", HEADERS)).append("\r\n");
        for (var row : rows) {
            for (int index = 0; index < HEADERS.size(); index++) {
                if (index > 0) csv.append(',');
                csv.append(quote(row.get(HEADERS.get(index))));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }

    private static String evidenceOrder(Object value) {
        if (!(value instanceof List<?> list)) return "";
        return list.stream().filter(Map.class::isInstance).map(item -> {
            Map<?, ?> triple = (Map<?, ?>) item;
            return text(triple.get("subject")) + "--" + text(triple.get("predicate")) + "-->" + text(triple.get("object"));
        }).filter(s -> !s.equals("---->" )).reduce((a, b) -> a + " | " + b).orElse("");
    }
    private static String first(Map<?, ?> map, String first, String second) { return map.get(first) == null ? text(map.get(second)) : text(map.get(first)); }
    private static boolean nonBlank(Object value) { return value != null && !String.valueOf(value).isBlank(); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String quote(Object value) { String text = value == null ? "" : String.valueOf(value).replace("\"", "\"\""); return text.contains(",") || text.contains("\n") || text.contains("\r") ? "\"" + text + "\"" : text; }
}
