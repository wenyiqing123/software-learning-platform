package edu.wfust.learning.ai;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeynmanEvaluateRequest(
        @NotBlank(message = "知识点不能为空")
        @Size(max = 100, message = "知识点名称不能超过 100 个字符")
        String knowledgePoint,
        @NotBlank(message = "学生讲解内容不能为空")
        @Size(min = 10, max = 2000, message = "讲解内容长度需在 10 到 2000 个字符之间")
        String studentExplanation,
        Long courseId
) {
    @AssertTrue(message = "讲解内容有效字符不能少于 10 个")
    public boolean isValidTrimmedExplanation() {
        return studentExplanation == null || studentExplanation.trim().length() >= 10;
    }
}
