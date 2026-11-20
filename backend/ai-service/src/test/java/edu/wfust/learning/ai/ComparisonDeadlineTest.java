package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComparisonDeadlineTest {
    private AnswerService service(ChatModel model, PythonKnowledgeClient python, ComparisonExecutor executor) {
        var client = new AiModelClient(new ModelSettings("https://provider.example/v1", "test-key", "fixed-model",
                0.2, 128, Duration.ofSeconds(30)), model);
        var json = JsonMapper.builder().build();
        return new AnswerService(client, python, new PromptStrategy(json), executor, new GroundedRankedAnswer(client, json));
    }

    @Test void globalDeadlineBoundsSevenSlowArmsAndCancelsRunningWork() throws Exception {
        ChatModel model = mock(ChatModel.class);
        PythonKnowledgeClient python = mock(PythonKnowledgeClient.class);
        CountDownLatch started = new CountDownLatch(7), interrupted = new CountDownLatch(7);
        when(python.retrieveUntruncated("question")).thenReturn(new PythonKnowledgeResponse("question", List.of(),
                List.of(new PythonTriple("s", "p", "o", 0.2)), "ORIGINAL", true, "template"));
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            started.countDown();
            try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            throw new IllegalStateException("interrupted provider body sk-sensitive");
        });
        try (ComparisonExecutor executor = new ComparisonExecutor(Duration.ofMillis(700))) {
            var results = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> service(model, python, executor).compare("question", true));
            assertThat(started.getCount()).isZero();
            assertThat(results).hasSize(7).allSatisfy(result -> {
                assertThat(result.errorCode()).isEqualTo("COMPARISON_TIMEOUT");
                assertThat(result.error()).doesNotContain("sk-sensitive");
            });
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        }
        verify(python, times(1)).retrieveUntruncated("question");
    }

    @Test void blockedRetrievalTimesOutButAlreadyCompletedDirectIsRetained() throws Exception {
        ChatModel model = mock(ChatModel.class);
        PythonKnowledgeClient python = mock(PythonKnowledgeClient.class);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("DIRECT OK")))));
        when(python.retrieve(anyString(), anyList(), anyList())).thenAnswer(invocation -> {
            try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            throw new IllegalStateException("stopped retrieval");
        });
        try (ComparisonExecutor executor = new ComparisonExecutor(Duration.ofMillis(250))) {
            var results = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> service(model, python, executor).compare("question"));
            assertThat(results.getFirst().answer()).isEqualTo("DIRECT OK");
            assertThat(results.getFirst().error()).isNull();
            assertThat(results.subList(1, 3)).allSatisfy(result -> assertThat(result.errorCode()).isEqualTo("COMPARISON_TIMEOUT"));
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        }
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test void deadlineConfigurationCannotExceed180Seconds() {
        assertThatThrownBy(() -> new ComparisonExecutor(Duration.ofSeconds(181))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComparisonExecutor(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
