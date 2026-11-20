package edu.wfust.learning.ai;

import tools.jackson.databind.ObjectMapper;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PromptStrategy {
    private final ObjectMapper objectMapper;
    public PromptStrategy(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public String build(QaMode mode, String question, PythonKnowledgeResponse knowledge) {
        if (mode == QaMode.DIRECT) return question;
        if (knowledge == null) throw AiFailure.knowledge(null);
        if (mode == QaMode.RANKED) {
            if (knowledge.prompt() == null || knowledge.prompt().isBlank())
                throw new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "PYTHON_PROMPT_MISSING",
                        "Python 知识服务未返回原始 Prompt，无法运行 RANKED。", true);
            return knowledge.prompt();
        }
        List<PythonTriple> triples = orderedTriples(mode, knowledge.triples());
        List<?> rows = triples.stream().map(triple -> mode.injectsScores()
                ? List.of(triple.subject(), triple.predicate(), triple.object(), triple.score())
                : List.of(triple.subject(), triple.predicate(), triple.object())).toList();
        // All factorial variants use exactly this template. Only ordering and score visibility vary.
        return "请根据以下课程知识三元组回答问题。若证据不足，请明确说明。\n三元组："
                + objectMapper.writeValueAsString(rows) + "\n问题：" + question;
    }

    public List<PythonTriple> orderedTriples(QaMode mode, List<PythonTriple> triples) {
        // Match prompt_builder.py: its original is_sort=True order is ascending, with stable ties.
        if (mode == QaMode.ABLATION_SORTED_SCORED || mode == QaMode.ABLATION_WITHOUT_SCORE)
            return triples.stream().sorted(Comparator.comparingDouble(PythonTriple::score)).toList();
        return triples;
    }
}
