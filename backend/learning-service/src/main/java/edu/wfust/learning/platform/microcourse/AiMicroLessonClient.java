package edu.wfust.learning.platform.microcourse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AiMicroLessonClient {
    private final RestClient client;

    public AiMicroLessonClient(@Value("${ai.service.base-url:http://127.0.0.1:8082}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(180));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public GenerationResult generate(String authorization, MicroLessonService.SourceBundle source) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("question", buildPrompt(source));
            Map<String, Object> response = client.post().uri("/api/ai/micro-lesson")
                .header("Authorization", authorization == null ? "" : authorization)
                .body(payload).retrieve().body(new ParameterizedTypeReference<>() { });
            String answer = response == null || response.get("answer") == null ? "" : String.valueOf(response.get("answer"));
            if (answer.isBlank()) return new GenerationResult(fallbackText(source));
            return new GenerationResult(answer);
        } catch (RestClientException | IllegalStateException exception) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "AI 微课生成服务暂不可用，请检查 AI 服务和模型配置", exception);
        }
    }

    static String buildPrompt(MicroLessonService.SourceBundle source) {
        String triples = source.triples().stream().map(item -> "- " + item.subject() + " — " + item.predicate() + " — " + item.object())
            .collect(Collectors.joining("\n"));
        String errors = source.commonErrors().stream().map(item -> "- " + item.stem() + "：" + item.explanation())
            .collect(Collectors.joining("\n"));
        String tests = source.selfTests().stream().map(item -> "- " + item.stem()).collect(Collectors.joining("\n"));
        String prerequisites = source.prerequisites().stream().map(MicroLessonService.PrerequisiteView::name).collect(Collectors.joining("、"));
        String content = source.contentJson() == null ? "" : source.contentJson();
        if (content.length() > 500) content = content.substring(0, 500);
        String prompt = "请为软件工程课程小节生成一份简洁的学习微课。只能使用下方真实课程来源和图谱三元组，不得补造来源；如果来源没有覆盖某个内容，明确写‘课程来源未提供’。输出包括：摘要、学习目标解释、关键三元组含义、常见错误提醒、自测建议和先修知识。不要修改课程正文。\n"
            + "小节：" + source.title() + "\n学习目标：" + String.join("；", source.objectives())
            + "\n图谱三元组：\n" + triples + "\n聚合错题：\n" + errors + "\n自测题：\n" + tests
            + "\n先修知识：" + prerequisites + "\n课程正文摘要：" + content;
        return prompt.length() > 1900 ? prompt.substring(0, 1900) : prompt;
    }

    static String fallbackText(MicroLessonService.SourceBundle source) {
        String triples = source.triples().stream()
            .map(item -> item.subject() + " — " + item.predicate() + " — " + item.object())
            .collect(Collectors.joining("；"));
        return "证据草稿：模型未返回可用正文，本次内容仅依据已保存的课程来源生成，发布前需要管理员审核。\n"
            + "学习目标：" + String.join("；", source.objectives()) + "\n关键图谱证据：" + triples;
    }

    public record GenerationResult(String text) { }
}
