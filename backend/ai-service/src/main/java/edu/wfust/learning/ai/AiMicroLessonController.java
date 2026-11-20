package edu.wfust.learning.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal long-form generation boundary for evidence-constrained course micro-lessons. */
@RestController
@RequestMapping("/api/ai")
public class AiMicroLessonController {
    private static final int MICRO_LESSON_MAX_TOKENS = 2048;
    private static final String SYSTEM_PROMPT = """
        你是软件工程课程助教。请直接输出可供学习者阅读的中文微课正文，不要只输出思考过程，
        不要输出隐藏推理、分析草稿或 <think> 标签。只能使用用户消息中的课程来源和图谱三元组；
        来源未覆盖的内容必须写“课程来源未提供”。正文必须包含摘要、学习目标解释、关键三元组含义、
        常见错误提醒、自测建议和先修知识；即使来源很少，也要输出简洁正文，不得返回空内容。
        """;

    private final AiModelClient model;

    public AiMicroLessonController(@Qualifier("aiModelClient") AiModelClient model) { this.model = model; }

    @PostMapping("/micro-lesson")
    public MicroLessonResponse generate(@Valid @RequestBody MicroLessonRequest request) {
        var response = model.call(request.question(), SYSTEM_PROMPT, MICRO_LESSON_MAX_TOKENS);
        String answer = response.getResult().getOutput().getText();
        return new MicroLessonResponse(answer == null ? "" : answer,
                AnswerResponse.TokenUsage.from(response));
    }

    public record MicroLessonRequest(@NotBlank @Size(max = 1900) String question) { }
    public record MicroLessonResponse(String answer, AnswerResponse.TokenUsage usage) { }
}
