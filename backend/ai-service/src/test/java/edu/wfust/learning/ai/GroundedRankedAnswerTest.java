package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroundedRankedAnswerTest {
    private final ChatModel model = mock(ChatModel.class);
    private final GroundedRankedAnswer service = new GroundedRankedAnswer(new AiModelClient(new ModelSettings(
            "https://provider.example/v1", "test-key", "fixed-model", .2, 1024, Duration.ofSeconds(2)), model),
            JsonMapper.builder().build());
    private final PythonKnowledgeResponse evidence = new PythonKnowledgeResponse("比较两类文档", List.of(), List.of(
            new PythonTriple("需求规格说明书", "用途", "记录软件需求", .9),
            new PythonTriple("设计说明书", "用途", "记录系统设计", .7)), "ORIGINAL", true, "template");
    private final PythonKnowledgeResponse definitionAndPurpose = new PythonKnowledgeResponse(
            "什么是数据流图DFD？它的主要作用是什么？", List.of(), List.of(
            new PythonTriple("数据流图DFD", "定义", "描述数据在系统中流动和处理的逻辑过程", .9),
            new PythonTriple("功能模型", "工具", "数据流图DFD--描述变换和加工逻辑", .8)),
            "ORIGINAL", true, "template");

    private ChatResponse reply(String value) { return new ChatResponse(List.of(new Generation(new AssistantMessage(value)))); }
    private void answers(String selection, String generated) {
        when(model.call(any(Prompt.class))).thenReturn(reply(selection), reply(generated));
    }
    private GroundedRankedAnswer.Outcome run() { return run("比较两类文档"); }
    private GroundedRankedAnswer.Outcome run(String question) { return service.execute(question, evidence, evidence.prompt()); }

    @Test void nullKnowledgeReturnsStructuredInsufficientEvidenceFailure() {
        var result = service.execute("问题", null, "原始提示");
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().code()).isEqualTo("KNOWLEDGE_INSUFFICIENT");
        assertThat(result.answer()).isEmpty();
    }

    @Test void insufficientEvidenceStopsBeforeAnswerGeneration() {
        answers("{\"status\":\"INSUFFICIENT\",\"taskType\":\"COMPARISON\",\"selectedIds\":[]}", "SHOULD NOT RUN");
        var result = run();
        assertThat(result.failure().code()).isEqualTo("KNOWLEDGE_INSUFFICIENT");
        assertThat(result.answer()).isEmpty();
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void unknownEvidenceIdCannotBePromotedToAnAnswer() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T99\"]}", "SHOULD NOT RUN");
        var result = run();
        assertThat(result.failure().code()).isEqualTo("EVIDENCE_SELECTION_INVALID");
        assertThat(result.answer()).isEmpty();
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void comparisonKeepsEveryRequiredFact() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"COMPARISON\",\"selectedIds\":[\"T1\",\"T2\"]}",
                "{\"answer\":\"需求规格说明书用途是记录软件需求；设计说明书用途是记录系统设计。\",\"usedIds\":[\"T1\",\"T2\"]}");
        var result = run();
        assertThat(result.failure()).isNull();
        assertThat(result.answer()).contains("需求规格说明书", "用途", "记录软件需求", "设计说明书", "记录系统设计");
        assertThat(result.trace().selectedIds()).containsExactly("T1", "T2");
        assertThat(result.usage()).isNull();
    }

    @Test void answerMissingSelectedPredicateIsRejected() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}",
                "{\"answer\":\"需求规格说明书：记录软件需求。\",\"usedIds\":[\"T1\"]}");
        var result = run("需求规格说明书记录什么？");
        assertThat(result.failure().code()).isEqualTo("ANSWER_EVIDENCE_MISMATCH");
        assertThat(result.answer()).isEmpty();
        assertThat(result.trace().generationRawResponse()).contains("需求规格说明书：记录软件需求");
    }

    @Test void generationPromptRequiresSubjectPredicateAndObject() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}",
                "{\"answer\":\"需求规格说明书用途是记录软件需求。\",\"usedIds\":[\"T1\"]}");
        var result = run("需求规格说明书记录什么？");
        assertThat(result.failure()).isNull();

        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(1).getInstructions().getFirst().getText())
                .contains("实体主体", "关系", "宾语", "规则、类型、方式、形式", "不得插入“是/为”或冒号",
                        "实质、定义、含义", "名词性判断关系", "用“是/为”等自然连接词", "不要机械在关系后插入冒号");
    }

    @Test void citingAllIdsDoesNotHideAnOmittedFact() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"COMPARISON\",\"selectedIds\":[\"T1\",\"T2\"]}",
                "{\"answer\":\"需求规格说明书记录软件需求。\",\"usedIds\":[\"T1\",\"T2\"]}");
        var result = run();
        assertThat(result.failure().code()).isEqualTo("ANSWER_EVIDENCE_MISMATCH");
        assertThat(result.answer()).isEmpty();
        assertThat(result.trace().generationRawResponse()).contains("需求规格说明书记录软件需求");
    }

    @Test void addedExamplesAreRejectedWithoutSilentlyReplacingTheModelOutput() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}",
                "{\"answer\":\"需求规格说明书记录软件需求，例如需求版本列表。\",\"usedIds\":[\"T1\"]}");
        var result = run("需求规格说明书记录什么？");
        assertThat(result.failure().code()).isEqualTo("ANSWER_EVIDENCE_MISMATCH");
        assertThat(result.answer()).isEmpty();
        assertThat(result.trace().generationRawResponse()).contains("例如");
    }

    @Test void conflictingEvidenceIsReportedAndDoesNotGenerate() {
        answers("{\"status\":\"CONFLICT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\",\"T2\"]}", "SHOULD NOT RUN");
        var result = run();
        assertThat(result.failure().code()).isEqualTo("KNOWLEDGE_CONFLICT");
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void malformedProviderOutputProducesAnExplicitFailure() {
        answers("prefix {\"selectedIds\":[\"T1\"]} fabricated suffix", "SHOULD NOT RUN");
        assertThat(run().failure().code()).isEqualTo("EVIDENCE_SELECTION_INVALID");
    }

    @Test void comparisonCannotBeClassifiedAsSingleFactAndDropOneSide() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}", "SHOULD NOT RUN");
        var result = run("比较需求规格说明书和设计说明书的用途分别是什么？");
        assertThat(result.failure().code()).isEqualTo("EVIDENCE_SELECTION_INCOMPLETE");
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void multiFactTaskMustSelectAtLeastTwoEvidenceItems() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"COMPARISON\",\"selectedIds\":[\"T1\"]}", "SHOULD NOT RUN");
        var result = run("比较两类文档");
        assertThat(result.failure().code()).isEqualTo("EVIDENCE_SELECTION_INCOMPLETE");
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void multiQuestionCannotBeDowngradedToSingleFact() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}", "SHOULD NOT RUN");
        var result = run("请分别说明需求规格说明书和设计说明书的用途？");
        assertThat(result.failure().code()).isEqualTo("EVIDENCE_SELECTION_INCOMPLETE");
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void multipleQuestionsCannotBeDowngradedToSingleFact() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}", "SHOULD NOT RUN");
        var result = run("需求规格说明书是什么？设计说明书是什么？");
        assertThat(result.failure().code()).isEqualTo("EVIDENCE_SELECTION_INCOMPLETE");
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void multiFactClassificationRequiresAtLeastTwoEvidenceItems() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"MULTI_FACT\",\"selectedIds\":[\"T1\"]}", "SHOULD NOT RUN");
        var result = run("列出相关事实");
        assertThat(result.failure().code()).isEqualTo("EVIDENCE_SELECTION_INCOMPLETE");
        assertThat(result.trace().modelCallCount()).isEqualTo(1);
    }

    @Test void twoRetrievedCandidatesCompleteAStableMultiQuestionSelection() {
        when(model.call(any(Prompt.class))).thenReturn(
                reply("{\"status\":\"SUFFICIENT\",\"taskType\":\"MULTI_FACT\",\"selectedIds\":[\"T1\"]}"),
                reply("{\"answer\":\"数据流图DFD定义描述数据在系统中流动和处理的逻辑过程；功能模型工具数据流图DFD--描述变换和加工逻辑。\",\"usedIds\":[\"T1\",\"T2\"]}"));

        var result = service.execute(definitionAndPurpose.question(), definitionAndPurpose, definitionAndPurpose.prompt());

        assertThat(result.failure()).isNull();
        assertThat(result.trace().selectedIds()).containsExactly("T1", "T2");
        assertThat(result.answer()).contains("定义", "描述数据在系统中流动和处理的逻辑过程", "工具", "描述变换和加工逻辑");
        assertThat(result.trace().modelCallCount()).isEqualTo(2);
    }

    @Test void multiQuestionAddsHighestScoredRetrievedFactWhenSelectorDropsOne() {
        var candidates = new PythonKnowledgeResponse(
                "什么是数据流图DFD？它的主要作用是什么？", List.of(), List.of(
                new PythonTriple("数据流图DFD", "定义", "描述数据在系统中流动和处理的逻辑过程", .8),
                new PythonTriple("功能模型", "工具", "数据流图DFD--描述变换和加工逻辑", .7),
                new PythonTriple("数据流", "符号", "箭头线", .99)),
                "ORIGINAL", true, "template");
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}",
                "{\"answer\":\"数据流图DFD定义描述数据在系统中流动和处理的逻辑过程；功能模型工具数据流图DFD--描述变换和加工逻辑。\",\"usedIds\":[\"T1\",\"T2\"]}");

        var result = service.execute(candidates.question(), candidates, candidates.prompt());

        assertThat(result.failure()).isNull();
        assertThat(result.trace().selectedIds()).containsExactly("T1", "T2");
    }

    @Test void exactObjectOnlyAnswerGetsAuditedSubjectAndPredicateFromTheActualEvidence() {
        answers("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}",
                "{\"answer\":\"记录软件需求。\",\"usedIds\":[\"T1\"]}");
        var result = run("需求规格说明书记录什么？");
        assertThat(result.failure()).isNull();
        assertThat(result.answer()).isEqualTo("需求规格说明书用途：记录软件需求。");
        assertThat(result.trace().answerNormalization()).isEqualTo("PREFIX_EVIDENCE_SUBJECT_PREDICATE");
        assertThat(result.trace().modelAnswer()).isEqualTo("记录软件需求。");
        assertThat(result.trace().evaluatedAnswer()).isEqualTo(result.answer());
    }
}
