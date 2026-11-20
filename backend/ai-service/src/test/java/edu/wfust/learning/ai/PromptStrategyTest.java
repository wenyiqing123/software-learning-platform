package edu.wfust.learning.ai;

import tools.jackson.databind.json.JsonMapper;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptStrategyTest {
    private final PromptStrategy strategy = new PromptStrategy(JsonMapper.builder().build());
    private final PythonKnowledgeResponse evidence = new PythonKnowledgeResponse(
            "需求分析是什么？",
            List.of(),
            List.of(new PythonTriple("需求分析", "交付文档", "软件需求规格说明书", 0.91)),
            "ORIGINAL PYTHON PROMPT",
            true,
            "make_qa4ft.prompt_template"
    );

    @Test
    void directModeUsesTheQuestionWithoutKnowledgeRetrieval() {
        assertThat(strategy.build(QaMode.DIRECT, "需求分析是什么？", null))
                .isEqualTo("需求分析是什么？");
    }

    @Test
    void tripleModeIncludesFactsButNeverIncludesRelevanceScores() {
        String prompt = strategy.build(QaMode.TRIPLES, "需求分析是什么？", evidence);

        assertThat(prompt).contains("需求分析", "交付文档", "软件需求规格说明书", "需求分析是什么？");
        assertThat(prompt).doesNotContain("0.91", "相关性");
    }

    @Test
    void rankedModePassesThePythonPromptThroughVerbatim() {
        assertThat(strategy.build(QaMode.RANKED, "ignored", evidence))
                .isEqualTo("ORIGINAL PYTHON PROMPT");
    }
}
