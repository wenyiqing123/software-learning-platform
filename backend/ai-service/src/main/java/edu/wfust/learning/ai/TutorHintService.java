package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import edu.wfust.learning.ai.client.PythonKnowledgeResponse;
import edu.wfust.learning.ai.client.PythonTriple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TutorHintService {
    private static final int MAX_EVIDENCE = 5;
    private final AiModelClient model;
    private final KnowledgeRetrievalEnhancer retrievalEnhancer;

    public TutorHintService(PythonKnowledgeClient knowledge, @Qualifier("aiModelClient") AiModelClient model) {
        this(knowledge, model, new KnowledgeRetrievalEnhancer(knowledge));
    }

    @Autowired
    TutorHintService(PythonKnowledgeClient knowledge, @Qualifier("aiModelClient") AiModelClient model,
                     KnowledgeRetrievalEnhancer retrievalEnhancer) {
        this.model = model;
        this.retrievalEnhancer = retrievalEnhancer;
    }

    public TutorHintResponse hint(TutorHintRequest request) {
        PythonKnowledgeResponse response = retrievalEnhancer.retrieve(
                request.question(), request.entities(), request.historyMessages(), request.historyEntities(), true);
        List<TutorHintResponse.HintEvidence> evidence = evidence(response);
        if (response == null || !response.hasEvidence() || evidence.isEmpty()) {
            return new TutorHintResponse(request.question(), request.level(),
                    "当前课程知识图谱没有检索到足够证据，暂不展开提示。请先换一个更具体的知识点或查看课程正文。",
                    false, List.of(), false, "NO_EVIDENCE");
        }
        return switch (request.level()) {
            case 1 -> new TutorHintResponse(request.question(), 1,
                    direction(evidence), true, evidence, true, "GRAPH_DIRECTION");
            case 2 -> new TutorHintResponse(request.question(), 2,
                    evidenceText(evidence), true, evidence, true, "GRAPH_EVIDENCE");
            case 3 -> new TutorHintResponse(request.question(), 3,
                    completeAnswer(request.question(), evidence), true, evidence, false, "GRAPH_GROUNDED_MODEL");
            default -> throw new IllegalArgumentException("提示层级必须是 1、2 或 3");
        };
    }

    private List<TutorHintResponse.HintEvidence> evidence(PythonKnowledgeResponse response) {
        return response.triples().stream().limit(MAX_EVIDENCE)
                .map(triple -> new TutorHintResponse.HintEvidence(triple.subject(), triple.predicate(), triple.object(), triple.score()))
                .toList();
    }

    private String direction(List<TutorHintResponse.HintEvidence> evidence) {
        String subjects = evidence.stream().map(TutorHintResponse.HintEvidence::subject).distinct().limit(3).collect(Collectors.joining("、"));
        return "思考方向：先定位问题涉及的知识点，再观察“谁”和“谁之间有什么关系”。本题可以先从这些实体入手：" + subjects + "。不要急着直接背结论，先判断题目要求的是定义、关系还是处理规则。";
    }

    private String evidenceText(List<TutorHintResponse.HintEvidence> evidence) {
        return "相关课程证据：\n" + evidence.stream()
                .map(item -> "- " + item.subject() + " — " + item.predicate() + " — " + item.object())
                .collect(Collectors.joining("\n"));
    }

    private String completeAnswer(String question, List<TutorHintResponse.HintEvidence> evidence) {
        String triples = evidence.stream().map(item -> "[" + item.subject() + " | " + item.predicate() + " | " + item.object() + "]")
                .collect(Collectors.joining("\n"));
        String system = "你是软件工程课程助教。只根据给出的课程证据回答问题；证据不足时明确说明。输出完整、简洁、适合学习者复习的答案和一句解释，不要编造证据。";
        var response = model.call("问题：" + question + "\n课程证据：\n" + triples, system);
        String answer = response.getResult().getOutput().getText();
        return answer == null || answer.isBlank() ? "模型没有生成可用解析，请回到课程正文核对。" : answer;
    }
}
