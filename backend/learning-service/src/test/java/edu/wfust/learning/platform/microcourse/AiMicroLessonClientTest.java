package edu.wfust.learning.platform.microcourse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiMicroLessonClientTest {
    @Test
    void promptFitsExistingAiQuestionLimitAndKeepsGraphEvidence() {
        var source = new MicroLessonService.SourceBundle("数据流图", List.of("识别加工"), "x".repeat(6000),
            List.of(new MicroLessonService.TripleView("加工", "命名", "动宾短语", 0.9)), List.of(), List.of(), List.of());

        String prompt = AiMicroLessonClient.buildPrompt(source);

        assertThat(prompt.length()).isLessThanOrEqualTo(1900);
        assertThat(prompt).contains("加工", "动宾短语");
    }

    @Test
    void fallbackTextIsExplicitAndEvidenceBoundWhenModelReturnsBlank() {
        var source = new MicroLessonService.SourceBundle("数据流图", List.of("识别加工"), "正文", List.of(
            new MicroLessonService.TripleView("加工", "命名", "动宾短语", 0.9)), List.of(), List.of(), List.of());

        String fallback = AiMicroLessonClient.fallbackText(source);

        assertThat(fallback).contains("模型未返回可用正文", "加工", "动宾短语");
    }
}
