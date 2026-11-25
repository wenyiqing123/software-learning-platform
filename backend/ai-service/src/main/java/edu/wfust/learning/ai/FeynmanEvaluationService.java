package edu.wfust.learning.ai;

import edu.wfust.learning.ai.FeynmanEvaluateResponse.TripleEvidence;
import edu.wfust.learning.ai.client.KnowledgeServiceUnavailableException;
import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FeynmanEvaluationService {
    private static final int MAX_EVIDENCE = 8;
    private static final int MAX_LIST_ITEMS = 8;
    private static final Pattern FENCE_PATTERN = Pattern.compile("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_COMMA_PATTERN = Pattern.compile(",\\s*([}\\]])");

    private final KnowledgeRetrievalEnhancer retrievalEnhancer;
    private final AiModelClient model;
    private final ObjectMapper objectMapper;

    public FeynmanEvaluationService(PythonKnowledgeClient knowledgeClient,
                                    @Qualifier("aiModelClient") AiModelClient model,
                                    ObjectMapper objectMapper) {
        this(new KnowledgeRetrievalEnhancer(knowledgeClient), model, objectMapper);
    }

    @Autowired
    public FeynmanEvaluationService(KnowledgeRetrievalEnhancer retrievalEnhancer,
                                    @Qualifier("aiModelClient") AiModelClient model,
                                    ObjectMapper objectMapper) {
        this.retrievalEnhancer = retrievalEnhancer;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public FeynmanEvaluateResponse evaluate(FeynmanEvaluateRequest request) {
        String topic = request.knowledgePoint().trim();
        String explanation = request.studentExplanation().trim();

        PythonKnowledgeResponse knowledge = retrievalEnhancer.retrieve(
                topic, List.of(topic), List.of(), List.of(), true);
        if (knowledge == null) {
            throw new KnowledgeServiceUnavailableException("知识检索服务暂不可用或响应不符合契约，请稍后重试。", null);
        }

        List<TripleEvidence> evidenceTriples = deduplicateEvidence(knowledge.triples());
        if (!knowledge.hasEvidence() || evidenceTriples.isEmpty()) {
            return new FeynmanEvaluateResponse(
                    topic,
                    List.of(),
                    List.of(),
                    List.of("当前课程知识图谱暂未检索到「" + topic + "」对应的三元组证据，暂不给出对照打分，建议结合课程章节核对核心定义或更换检索概念后再自检。"),
                    "你能结合课程章节内容，说说「" + topic + "」与前后知识点之间有什么关联吗？",
                    List.of()
            );
        }

        String systemPrompt = buildEvaluationSystemPrompt();
        String userPrompt = buildEvaluationUserPrompt(topic, explanation, evidenceTriples);
        var modelResponse = model.call(userPrompt, systemPrompt);
        String rawText = modelResponse.getResult().getOutput().getText();

        return parseAndGroundResponse(topic, explanation, rawText, evidenceTriples);
    }

    private String buildEvaluationSystemPrompt() {
        return """
                你是软件工程课程的费曼“以教促学”辅导助手。请严格对照给定的课程知识图谱三元组，诊断学生用自己语言讲述的概念理解。
                只输出一个合法的 JSON 对象，不要附加额外解释或前后缀，包含且仅包含以下字段：
                - "coveredPoints": 字符串数组，总结学生解释中准确覆盖的知识点要点（不超过 6 条）。
                - "missingTriples": 对象数组，仅从给定图谱证据中挑选学生讲解遗漏的关键三元组，每项格式为 {"subject":"...","predicate":"...","object":"..."}；严禁编造给定列表之外的三元组。
                - "misconceptions": 字符串数组，指出概念混淆、片面理解或术语误用之处；没有明显误区时返回空数组 []。
                - "followUpQuestion": 字符串，围绕遗漏或薄弱处提出 1 个简洁的启发式追问，引导学习者进一步补充讲解。
                """.strip();
    }

    private String buildEvaluationUserPrompt(String topic, String explanation, List<TripleEvidence> evidence) {
        String tripleLines = evidence.stream()
                .map(t -> "[" + t.subject() + " | " + t.predicate() + " | " + t.object() + "]")
                .collect(Collectors.joining("\n"));
        return "目标知识点：" + topic
                + "\n课程知识图谱证据三元组：\n" + tripleLines
                + "\n\n学生讲解文本：\n" + explanation;
    }

    private FeynmanEvaluateResponse parseAndGroundResponse(
            String topic,
            String explanation,
            String rawText,
            List<TripleEvidence> evidenceTriples
    ) {
        Map<String, TripleEvidence> evidenceLookup = new LinkedHashMap<>();
        for (TripleEvidence triple : evidenceTriples) {
            evidenceLookup.putIfAbsent(tripleKey(triple.subject(), triple.predicate(), triple.object()), triple);
        }

        JsonNode root = tryParseJsonObject(rawText);
        if (root != null && root.isObject()) {
            List<String> covered = readStringList(root.get("coveredPoints"));
            List<TripleEvidence> missing = filterGroundedMissingTriples(root.get("missingTriples"), evidenceLookup);
            List<String> misconceptions = readStringList(root.get("misconceptions"));
            String followUp = readNonBlankString(root.get("followUpQuestion"));
            if (followUp.isBlank()) {
                followUp = defaultFollowUpQuestion(topic, missing);
            }
            return new FeynmanEvaluateResponse(topic, covered, missing, misconceptions, followUp, evidenceTriples);
        }

        // 模型偶发未按严格 JSON 输出时的平滑结构化降级（不抛出 500，不捏造任何虚假三元组）
        List<TripleEvidence> deterministicMissing = new ArrayList<>();
        List<String> deterministicCovered = new ArrayList<>();
        String normalizedExplanation = normalizeForMatch(explanation);
        for (TripleEvidence triple : evidenceTriples) {
            boolean mentionsSubject = normalizedExplanation.contains(normalizeForMatch(triple.subject()));
            boolean mentionsObject = normalizedExplanation.contains(normalizeForMatch(triple.object()));
            if (mentionsSubject && mentionsObject) {
                deterministicCovered.add(triple.subject() + " — " + triple.predicate() + " — " + triple.object());
            } else {
                deterministicMissing.add(triple);
            }
        }
        String cleanedFeedback = rawText == null ? "" : rawText.trim();
        List<String> fallbackFeedback = cleanedFeedback.isBlank()
                ? List.of("本次已完成图谱要点比对，建议对照遗漏关联继续补充说明。")
                : List.of(cleanedFeedback);

        return new FeynmanEvaluateResponse(
                topic,
                limitList(deterministicCovered),
                List.copyOf(deterministicMissing),
                fallbackFeedback,
                defaultFollowUpQuestion(topic, deterministicMissing),
                evidenceTriples
        );
    }

    private JsonNode tryParseJsonObject(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        String candidate = stripMarkdownFences(rawText.trim());
        JsonNode parsed = readTreeSafe(candidate);
        if (parsed != null && parsed.isObject()) {
            return parsed;
        }
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String slice = candidate.substring(start, end + 1);
            parsed = readTreeSafe(slice);
            if (parsed != null && parsed.isObject()) {
                return parsed;
            }
            String repaired = TRAILING_COMMA_PATTERN.matcher(slice).replaceAll("$1");
            parsed = readTreeSafe(repaired);
            if (parsed != null && parsed.isObject()) {
                return parsed;
            }
        }
        return null;
    }

    private JsonNode readTreeSafe(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String stripMarkdownFences(String text) {
        Matcher matcher = FENCE_PATTERN.matcher(text);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return text;
    }

    private List<TripleEvidence> filterGroundedMissingTriples(
            JsonNode arrayNode,
            Map<String, TripleEvidence> evidenceLookup
    ) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        Map<String, TripleEvidence> grounded = new LinkedHashMap<>();
        for (JsonNode item : arrayNode) {
            if (item == null || item.isNull()) continue;
            String key = null;
            if (item.isObject()) {
                String subject = readNonBlankString(item.get("subject"));
                String predicate = readNonBlankString(item.get("predicate"));
                String object = readNonBlankString(item.get("object"));
                if (!subject.isEmpty() && !predicate.isEmpty() && !object.isEmpty()) {
                    key = tripleKey(subject, predicate, object);
                }
            } else if (item.isString()) {
                String[] parts = item.asString().split("\\s*(?:—|\\||->)\\s*", 3);
                if (parts.length == 3) {
                    key = tripleKey(parts[0], parts[1], parts[2]);
                }
            }
            if (key != null && evidenceLookup.containsKey(key)) {
                grounded.putIfAbsent(key, evidenceLookup.get(key));
            }
        }
        return List.copyOf(grounded.values());
    }

    private List<TripleEvidence> deduplicateEvidence(List<PythonTriple> rawTriples) {
        if (rawTriples == null || rawTriples.isEmpty()) {
            return List.of();
        }
        Map<String, TripleEvidence> unique = new LinkedHashMap<>();
        for (PythonTriple triple : rawTriples) {
            if (triple == null || triple.subject() == null || triple.predicate() == null || triple.object() == null) {
                continue;
            }
            String s = triple.subject().trim();
            String p = triple.predicate().trim();
            String o = triple.object().trim();
            if (s.isEmpty() || p.isEmpty() || o.isEmpty()) {
                continue;
            }
            String key = tripleKey(s, p, o);
            unique.putIfAbsent(key, new TripleEvidence(s, p, o, triple.score()));
            if (unique.size() >= MAX_EVIDENCE) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (JsonNode element : node) {
            if (element != null && !element.isNull()) {
                String value = element.isString() ? element.asString().trim() : element.toString().trim();
                if (!value.isBlank() && !items.contains(value)) {
                    items.add(value);
                }
                if (items.size() >= MAX_LIST_ITEMS) {
                    break;
                }
            }
        }
        return List.copyOf(items);
    }

    private List<String> limitList(List<String> source) {
        if (source.size() <= MAX_LIST_ITEMS) {
            return List.copyOf(source);
        }
        return List.copyOf(source.subList(0, MAX_LIST_ITEMS));
    }

    private String readNonBlankString(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isString() ? node.asString().trim() : "";
    }

    private String defaultFollowUpQuestion(String topic, List<TripleEvidence> missing) {
        if (!missing.isEmpty()) {
            TripleEvidence first = missing.getFirst();
            return "请补充解释「" + first.subject() + "」是如何通过“" + first.predicate() + "”关联到「" + first.object() + "」的？";
        }
        return "能否再举一个实际软件工程场景，说明「" + topic + "」是如何发挥作用的？";
    }

    private static String tripleKey(String subject, String predicate, String object) {
        return normalizeForMatch(subject) + "|" + normalizeForMatch(predicate) + "|" + normalizeForMatch(object);
    }

    private static String normalizeForMatch(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
