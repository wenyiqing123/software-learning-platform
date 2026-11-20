package edu.wfust.learning.ai;

import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure evidence rules kept separate from model calls and JSON selection parsing. */
final class GroundedEvidenceValidator {
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern MULTI_FACT_QUESTION = Pattern.compile("分别|比较|对比|区别|差异|各自|异同|相同点|不同点");
    private final ObjectMapper json;

    GroundedEvidenceValidator(ObjectMapper json) { this.json = json; }

    boolean coversFacts(String answer, List<GroundedRankedAnswer.Evidence> evidence) {
        String normalized = normalize(answer);
        for (var fact : evidence) {
            if (!normalized.contains(normalize(fact.subject()))
                    || !normalized.contains(normalize(fact.predicate()))
                    || !normalized.contains(normalize(fact.object()))) return false;
        }
        String facts = json.writeValueAsString(evidence);
        if ((answer.contains("例如") || answer.contains("比如")) && !facts.contains("例如") && !facts.contains("比如")) return false;
        var numbers = NUMBER.matcher(answer);
        String values = evidence.stream().map(e -> e.subject() + " " + e.predicate() + " " + e.object()).reduce("", (a, b) -> a + " " + b);
        Set<String> sourceNumbers = new HashSet<>();
        NUMBER.matcher(values).results().forEach(match -> sourceNumbers.add(match.group()));
        while (numbers.find()) if (!sourceNumbers.contains(numbers.group())) return false;
        return true;
    }

    boolean requiresMultipleEvidence(String question, String taskType) {
        String value = question == null ? "" : question;
        long questionMarks = value.chars().filter(c -> c == '?' || c == '？').limit(2).count();
        return !"SINGLE_FACT".equals(taskType) || MULTI_FACT_QUESTION.matcher(value).find() || questionMarks > 1;
    }

    double fallbackEvidenceScore(String question, GroundedRankedAnswer.Evidence candidate) {
        String questionText = question == null ? "" : question;
        String fact = (candidate.subject() + candidate.predicate() + candidate.object()).toLowerCase(Locale.ROOT);
        double score = candidate.score();
        if (containsAny(questionText, "作用", "功能", "用途", "目的")
                && containsAny(fact, "作用", "功能", "用途", "目的", "使用", "工具", "描述")) score += 1.0;
        if (containsAny(questionText, "什么是", "定义", "含义")
                && containsAny(fact, "定义", "含义", "描述")) score += 0.5;
        if (containsAny(questionText, "组成", "包含", "构成")
                && containsAny(fact, "组成", "包含", "构成")) score += 0.8;
        return score;
    }

    boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    String normalize(String value) {
        StringBuilder result = new StringBuilder();
        (value == null ? "" : value).toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit).forEach(result::appendCodePoint);
        return result.toString();
    }
}
