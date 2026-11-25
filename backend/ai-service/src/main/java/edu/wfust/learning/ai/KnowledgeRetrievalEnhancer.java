package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonEntity;
import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向日常助教问答与分步引导的图谱检索增强器。
 * 在保持外部 Python Bridge 与实验评估基线原样只读的前提下，负责：
 * 1) 高频课程术语别名补全，减少简称失配导致的局部前缀误切；
 * 2) 基于一跳结构化桥接尾实体进行有界二跳扩搜；
 * 3) 对真实检索三元组按主体焦点与相关性分数重排（不改写原始分值）。
 */
@Component
public class KnowledgeRetrievalEnhancer {
    private static final int MAX_REQUEST_ENTITIES = 10;
    private static final int MAX_BRIDGE_ENTITIES = 3;
    private static final Pattern PUNCTUATION = Pattern.compile("[\\s，。！？、；：,.!?;:（）()【】\\[\\]{}\"“”'‘’<>《》]");
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");

    /** 图谱内自带缩写/全称后缀导致词典子串未直接命中的术语映射 */
    private static final Map<String, AliasRule> COURSE_ALIAS_RULES = Map.ofEntries(
            Map.entry("数据流图", new AliasRule("数据流图DFD", "dfd")),
            Map.entry("数据字典", new AliasRule("数据字典DD", "dd")),
            Map.entry("状态转换图", new AliasRule("状态转换图STD", "std")),
            Map.entry("实体关系图", new AliasRule("实体关系图E-R图", "e-r")),
            Map.entry("e-r图", new AliasRule("实体关系图E-R图", "实体关系图")),
            Map.entry("er图", new AliasRule("实体关系图E-R图", "实体关系图")),
            Map.entry("需求规格说明书", new AliasRule("软件需求规格说明书", "软件需求规格说明书"))
    );

    private static final List<String> BRIDGE_PREDICATE_KEYWORDS = List.of(
            "包含", "包括", "组成", "元素", "注意", "步骤", "分类", "划分",
            "类型", "规则", "阶段", "环节", "结构", "细化"
    );

    private static final Set<String> STOP_BIGRAMS = Set.of(
            "如何", "怎么", "怎样", "什么", "哪些", "是否", "应该", "应当",
            "需要", "可以", "一般", "通常", "请问", "说明", "介绍", "解释",
            "含义", "区别", "作用", "中的", "之间", "相关", "主要", "具体"
    );

    private final PythonKnowledgeClient knowledgeClient;

    public KnowledgeRetrievalEnhancer(PythonKnowledgeClient knowledgeClient) {
        this.knowledgeClient = knowledgeClient;
    }

    /**
     * 执行增强检索：别名对齐 -> 1 跳检索 -> 有界 2 跳扩搜合并 -> 主体相关性排序。
     *
     * @param preferFourArgs 为 true 时统一走四参客户端重载，确保既有 mock 契约兼容。
     */
    public PythonKnowledgeResponse retrieve(
            String question,
            List<String> explicitEntities,
            List<String> historyMessages,
            List<String> historyEntities,
            boolean preferFourArgs
    ) {
        ResolutionPlan plan = buildResolutionPlan(question, explicitEntities);
        PythonKnowledgeResponse first = invokeBridge(
                question, plan.initialEntities(), historyMessages, historyEntities, preferFourArgs);
        if (first == null) {
            return null;
        }

        List<String> bridgeTargets = discoverBridgeEntities(question, first, plan);
        PythonKnowledgeResponse active = first;
        if (!bridgeTargets.isEmpty()) {
            List<String> combined = mergeEntitiesForRequery(plan.initialEntities(), first.entities(), bridgeTargets);
            try {
                PythonKnowledgeResponse expanded = invokeBridge(
                        question, combined, historyMessages, historyEntities, preferFourArgs);
                if (expanded != null && expanded.hasEvidence() && !expanded.triples().isEmpty()) {
                    active = expanded;
                }
            } catch (RuntimeException ignored) {
                // 二跳扩搜偶发异常时安全降级回一跳真实结果，避免整体问答不可用
            }
        }

        if (!active.hasEvidence() || active.triples().isEmpty()) {
            return active;
        }
        List<PythonTriple> prioritized = prioritizeTriples(
                question, active.triples(), plan, historyEntities, bridgeTargets);
        return new PythonKnowledgeResponse(
                active.question(), active.entities(), prioritized,
                active.prompt(), active.hasEvidence(), active.promptTemplate());
    }

    private PythonKnowledgeResponse invokeBridge(
            String question,
            List<String> entities,
            List<String> historyMessages,
            List<String> historyEntities,
            boolean preferFourArgs
    ) {
        List<String> safeEntities = entities == null ? List.of() : entities;
        List<String> safeHistoryMessages = historyMessages == null ? List.of() : historyMessages;
        List<String> safeHistoryEntities = historyEntities == null ? List.of() : historyEntities;
        if (!preferFourArgs && safeHistoryMessages.isEmpty() && safeHistoryEntities.isEmpty()) {
            return knowledgeClient.retrieve(question, safeEntities, safeHistoryMessages);
        }
        return knowledgeClient.retrieve(question, safeEntities, safeHistoryMessages, safeHistoryEntities);
    }

    ResolutionPlan buildResolutionPlan(String question, List<String> explicitEntities) {
        String normalizedQuestion = normalize(question);
        List<String> initial = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> explicitNormalized = new LinkedHashSet<>();

        if (explicitEntities != null) {
            for (String entity : explicitEntities) {
                if (entity == null || entity.isBlank()) continue;
                String trimmed = entity.trim();
                String key = normalize(trimmed);
                if (!key.isEmpty() && seen.add(key)) {
                    initial.add(trimmed);
                    explicitNormalized.add(key);
                }
            }
        }

        List<String> matchedCompoundSources = new ArrayList<>();
        for (Map.Entry<String, AliasRule> entry : COURSE_ALIAS_RULES.entrySet()) {
            String sourceKey = entry.getKey();
            AliasRule rule = entry.getValue();
            if (normalizedQuestion.contains(sourceKey)) {
                matchedCompoundSources.add(sourceKey);
                if (!normalizedQuestion.contains(rule.skipIfContains())) {
                    String canonicalKey = normalize(rule.canonical());
                    if (seen.add(canonicalKey) && initial.size() < MAX_REQUEST_ENTITIES) {
                        initial.add(rule.canonical());
                    }
                }
            }
        }
        return new ResolutionPlan(List.copyOf(initial), Set.copyOf(explicitNormalized), List.copyOf(matchedCompoundSources));
    }

    List<String> discoverBridgeEntities(String question, PythonKnowledgeResponse first, ResolutionPlan plan) {
        if (first.triples() == null || first.triples().isEmpty()) {
            return List.of();
        }
        Set<String> alreadyQueried = new LinkedHashSet<>(plan.explicitNormalized());
        for (String entity : plan.initialEntities()) {
            alreadyQueried.add(normalize(entity));
        }
        for (PythonEntity entity : first.entities()) {
            if (entity != null && entity.canonicalName() != null) {
                alreadyQueried.add(normalize(entity.canonicalName()));
            }
        }

        Set<String> questionTokens = extractMeaningfulBigrams(question);
        List<BridgeCandidate> candidates = new ArrayList<>();
        Set<String> seenObjects = new HashSet<>();

        for (PythonTriple triple : first.triples()) {
            if (triple == null || triple.object() == null || triple.predicate() == null || triple.subject() == null) {
                continue;
            }
            String objectText = triple.object().trim();
            String normalizedObject = normalize(objectText);
            if (normalizedObject.length() < 2 || objectText.length() > 18 || containsSentencePunctuation(objectText)) {
                continue;
            }
            if (alreadyQueried.contains(normalizedObject) || normalizedObject.equals(normalize(triple.subject()))) {
                continue;
            }
            if (!isExpansionPredicate(triple.predicate()) || !seenObjects.add(normalizedObject)) {
                continue;
            }

            // 计算尾实体带来多少“原主语尚未覆盖的新增问题关键词”
            Set<String> subjectTokens = extractMeaningfulBigrams(triple.subject());
            long newOverlap = extractMeaningfulBigrams(objectText).stream()
                    .filter(token -> questionTokens.contains(token) && !subjectTokens.contains(token))
                    .count();
            if (newOverlap > 0 || triple.score() >= 0.75) {
                candidates.add(new BridgeCandidate(objectText, newOverlap, triple.score()));
            }
        }

        return candidates.stream()
                .sorted(Comparator.<BridgeCandidate>comparingLong(BridgeCandidate::newOverlap).reversed()
                        .thenComparing(Comparator.comparingDouble(BridgeCandidate::score).reversed()))
                .limit(MAX_BRIDGE_ENTITIES)
                .map(BridgeCandidate::entityName)
                .toList();
    }

    private List<String> mergeEntitiesForRequery(
            List<String> initialEntities,
            List<PythonEntity> resolvedEntities,
            List<String> bridgeTargets
    ) {
        List<String> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addUnique(merged, seen, initialEntities);
        addUnique(merged, seen, bridgeTargets);
        if (resolvedEntities != null) {
            for (PythonEntity hit : resolvedEntities) {
                if (hit != null && hit.canonicalName() != null) {
                    addSingle(merged, seen, hit.canonicalName());
                }
            }
        }
        return merged.size() > MAX_REQUEST_ENTITIES ? List.copyOf(merged.subList(0, MAX_REQUEST_ENTITIES)) : List.copyOf(merged);
    }

    private List<PythonTriple> prioritizeTriples(
            String question,
            List<PythonTriple> triples,
            ResolutionPlan plan,
            List<String> historyEntities,
            List<String> bridgeTargets
    ) {
        String normalizedQuestion = normalize(question);
        Set<String> questionTokens = extractMeaningfulBigrams(question);
        Set<String> focusNormalized = new LinkedHashSet<>(plan.explicitNormalized());
        if (historyEntities != null) {
            historyEntities.stream().map(KnowledgeRetrievalEnhancer::normalize).filter(s -> !s.isEmpty()).forEach(focusNormalized::add);
        }
        Set<String> bridgeNormalized = new LinkedHashSet<>();
        for (String target : bridgeTargets) {
            bridgeNormalized.add(normalize(target));
        }

        List<String> compoundAnchors = new ArrayList<>(plan.matchedCompoundSources());
        for (String initial : plan.initialEntities()) {
            String key = normalize(initial);
            if (normalizedQuestion.contains(key)) {
                compoundAnchors.add(key);
            }
        }

        record ScoredTriple(PythonTriple triple, double effectiveScore) {}
        List<ScoredTriple> weighted = new ArrayList<>(triples.size());
        for (PythonTriple triple : triples) {
            String subjectKey = normalize(triple.subject());
            String objectKey = normalize(triple.object());

            boolean subsumedPrefix = isSubsumedPrefixNoise(
                    subjectKey, normalizedQuestion, compoundAnchors, plan.explicitNormalized(), historyEntities);
            double weight = triple.score();
            if (subsumedPrefix) {
                // 仅被长复合词包裹的局部分词（如“数据流图”误切出“数据流”）降低排序权重，避免遮蔽核心主题证据
                weight -= 0.20;
            } else {
                if (bridgeNormalized.contains(subjectKey) || focusNormalized.contains(subjectKey)) {
                    weight += 0.15;
                }
                if (bridgeNormalized.contains(objectKey) || containsAnyToken(objectKey, questionTokens)) {
                    weight += 0.05;
                }
            }
            weighted.add(new ScoredTriple(triple, weight));
        }
        return weighted.stream()
                .sorted(Comparator.comparingDouble(ScoredTriple::effectiveScore).reversed())
                .map(ScoredTriple::triple)
                .toList();
    }

    private boolean isSubsumedPrefixNoise(
            String candidateNormalized,
            String normalizedQuestion,
            List<String> compoundAnchors,
            Set<String> explicitNormalized,
            List<String> historyEntities
    ) {
        if (candidateNormalized.length() < 2 || explicitNormalized.contains(candidateNormalized)) {
            return false;
        }
        if (historyEntities != null && historyEntities.stream().anyMatch(h -> normalize(h).equals(candidateNormalized))) {
            return false;
        }
        List<int[]> anchorSpans = new ArrayList<>();
        for (String anchor : compoundAnchors) {
            if (anchor.length() <= candidateNormalized.length() || !anchor.contains(candidateNormalized)) continue;
            int idx = 0;
            while ((idx = normalizedQuestion.indexOf(anchor, idx)) >= 0) {
                anchorSpans.add(new int[]{idx, idx + anchor.length()});
                idx += anchor.length();
            }
        }
        if (anchorSpans.isEmpty()) {
            return false;
        }
        int foundAt = normalizedQuestion.indexOf(candidateNormalized);
        if (foundAt < 0) {
            return false;
        }
        int cursor = 0;
        while ((cursor = normalizedQuestion.indexOf(candidateNormalized, cursor)) >= 0) {
            int start = cursor;
            int end = cursor + candidateNormalized.length();
            boolean insideAnchor = anchorSpans.stream().anyMatch(span -> start >= span[0] && end <= span[1]);
            if (!insideAnchor) {
                return false;
            }
            cursor += candidateNormalized.length();
        }
        return true;
    }

    private static boolean containsAnyToken(String normalizedText, Set<String> tokens) {
        for (String token : tokens) {
            if (normalizedText.contains(token)) return true;
        }
        return false;
    }

    private static boolean isExpansionPredicate(String predicate) {
        for (String keyword : BRIDGE_PREDICATE_KEYWORDS) {
            if (predicate.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSentencePunctuation(String text) {
        return text.indexOf('，') >= 0 || text.indexOf('。') >= 0 || text.indexOf('；') >= 0
                || text.indexOf('：') >= 0 || text.indexOf('、') >= 0 || text.indexOf(',') >= 0;
    }

    private static Set<String> extractMeaningfulBigrams(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = CJK_RUN.matcher(text);
        while (matcher.find()) {
            String segment = matcher.group();
            for (int i = 0; i <= segment.length() - 2; i++) {
                String bigram = segment.substring(i, i + 2);
                if (!STOP_BIGRAMS.contains(bigram)) {
                    tokens.add(bigram);
                }
            }
        }
        return tokens;
    }

    private static void addUnique(List<String> target, Set<String> seen, List<String> items) {
        if (items == null) return;
        for (String item : items) {
            addSingle(target, seen, item);
        }
    }

    private static void addSingle(List<String> target, Set<String> seen, String raw) {
        if (raw == null || raw.isBlank() || target.size() >= MAX_REQUEST_ENTITIES) return;
        String trimmed = raw.trim();
        String key = normalize(trimmed);
        if (!key.isEmpty() && seen.add(key)) {
            target.add(trimmed);
        }
    }

    static String normalize(String value) {
        if (value == null) return "";
        return PUNCTUATION.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private record AliasRule(String canonical, String skipIfContains) {}
    private record BridgeCandidate(String entityName, long newOverlap, double score) {}
    record ResolutionPlan(List<String> initialEntities, Set<String> explicitNormalized, List<String> matchedCompoundSources) {}
}
