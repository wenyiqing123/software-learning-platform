package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnswerStreamSessionTest {
    private final ChatModel model = mock(ChatModel.class);
    private final PythonKnowledgeClient python = mock(PythonKnowledgeClient.class);
    private final ExecutorService workers = Executors.newSingleThreadExecutor();
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1);
    private final RecordingEmitter emitter = new RecordingEmitter();
    private final List<Throwable> dropped = new CopyOnWriteArrayList<>();
    private final ComparisonExecutor comparisons = new ComparisonExecutor(Duration.ofSeconds(150));
    private AnswerService service;

    @BeforeEach void setUp() {
        deadlines.setRemoveOnCancelPolicy(true);
        Hooks.onErrorDropped(dropped::add);
        var client = new AiModelClient(new ModelSettings("https://provider.example/v1", "test-key",
                "fixed-model", 0.2, 128, Duration.ofSeconds(2)), model);
        var json = JsonMapper.builder().build();
        service = new AnswerService(client, python, new PromptStrategy(json), comparisons, new GroundedRankedAnswer(client, json));
    }
    @AfterEach void close() throws Exception {
        comparisons.close(); workers.shutdownNow(); deadlines.shutdownNow();
        workers.awaitTermination(2, TimeUnit.SECONDS);
        deadlines.awaitTermination(2, TimeUnit.SECONDS);
        Hooks.resetOnErrorDropped();
    }
    private void start(QaMode mode, Duration timeout) {
        new AnswerStreamSession(service, new AnswerRequest("question", mode), emitter, workers, deadlines, timeout).start();
    }
    private void await(CountDownLatch latch) throws Exception { assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue(); }
    private ChatResponse text(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
    private AnswerService withDedicatedRankedModel(ChatModel rankedModel) {
        var defaultClient = new AiModelClient(new ModelSettings("https://provider.example/v1", "test-key",
                "fixed-model", 0.2, 128, Duration.ofSeconds(2)), model);
        var rankedClient = new AiModelClient(new ModelSettings("http://127.0.0.1:8998/v1", "qwen-secret",
                "qwen35-ranked-lora", 0.0, 512, Duration.ofSeconds(2)), rankedModel);
        var json = JsonMapper.builder().build();
        return new AnswerService(defaultClient, python, new PromptStrategy(json), comparisons,
                new GroundedRankedAnswer(defaultClient, json), new GroundedRankedAnswer(rankedClient, json), rankedClient);
    }

    private PythonKnowledgeResponse singleFactEvidence() {
        return new PythonKnowledgeResponse("question", List.of(),
                List.of(new PythonTriple("raw first", "rel", "high", 0.9)),
                "ORIGINAL PROMPT", true, "make_qa4ft.prompt_template");
    }

    private ChatResponse modelText(String value) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(value))),
                ChatResponseMetadata.builder().model("qwen35-ranked-lora").usage(new DefaultUsage(12, 6, 18)).build());
    }

    @Test void qwenRankedSseBuffersTheAnswerUntilEvidenceValidationPasses() throws Exception {
        when(python.retrieve(anyString(), anyList(), anyList())).thenReturn(singleFactEvidence());
        ChatModel rankedModel = mock(ChatModel.class);
        CountDownLatch generationEntered = new CountDownLatch(1), releaseGeneration = new CountDownLatch(1);
        when(rankedModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().getFirst().getText();
            if (system.contains("[EVIDENCE_SELECTION_V1]")) {
                return modelText("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}");
            }
            generationEntered.countDown();
            assertThat(releaseGeneration.await(2, TimeUnit.SECONDS)).isTrue();
            return modelText("{\"answer\":\"raw firstrel：high。\",\"usedIds\":[\"T1\"]}");
        });
        service = withDedicatedRankedModel(rankedModel);

        start(QaMode.RANKED, Duration.ofSeconds(3));
        await(generationEntered);
        assertThat(emitter.names()).containsExactly("status", "knowledge");

        releaseGeneration.countDown();
        await(emitter.finished);

        assertThat(emitter.names()).containsExactly("status", "knowledge", "delta", "done");
        assertThat(((Map<?, ?>) emitter.events.get(2).payload()).get("content")).isEqualTo("raw firstrel：high。");
        AnswerResponse result = (AnswerResponse) emitter.events.getLast().payload();
        assertThat(result.provenance().model()).isEqualTo("qwen35-ranked-lora");
        assertThat(result.grounding().status()).isEqualTo("EVIDENCE_CHECK_PASSED");
        verify(rankedModel, times(2)).call(any(Prompt.class));
        verifyNoInteractions(model);
    }

    @Test void qwenRankedSseEmitsNoAnswerDeltaWhenEvidenceValidationFails() throws Exception {
        when(python.retrieve(anyString(), anyList(), anyList())).thenReturn(singleFactEvidence());
        ChatModel rankedModel = mock(ChatModel.class);
        when(rankedModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = prompt.getInstructions().getFirst().getText();
            return system.contains("[EVIDENCE_SELECTION_V1]")
                    ? modelText("{\"status\":\"SUFFICIENT\",\"taskType\":\"SINGLE_FACT\",\"selectedIds\":[\"T1\"]}")
                    : modelText("{\"answer\":\"unsupported claim\",\"usedIds\":[\"T1\"]}");
        });
        service = withDedicatedRankedModel(rankedModel);

        start(QaMode.RANKED, Duration.ofSeconds(3));
        await(emitter.finished);

        assertThat(emitter.names()).containsExactly("status", "knowledge", "error");
        assertThat(emitter.names()).doesNotContain("delta", "done");
        verify(rankedModel, times(2)).call(any(Prompt.class));
        verifyNoInteractions(model);
    }

    @Test void synchronousFailureHasOneSafeErrorNoDoneAndNoDroppedReactiveError() throws Exception {
        when(model.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalStateException("Bearer sk-sensitive")));
        start(QaMode.DIRECT, Duration.ofSeconds(2));
        await(emitter.finished);
        assertThat(emitter.names()).containsExactly("status", "knowledge", "error");
        assertThat(emitter.events.getLast().payload().toString()).doesNotContain("sk-sensitive");
        assertThat(emitter.completions).isEqualTo(1);
        assertThat(dropped).isEmpty();
        assertThat(deadlines.getQueue()).isEmpty();
        verifyNoInteractions(python);
    }

    @Test void clientCompletionCancelsSubscriptionAndRemovesDeadline() throws Exception {
        Sinks.Many<ChatResponse> source = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch subscribed = new CountDownLatch(1), cancelled = new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenReturn(source.asFlux()
                .doOnSubscribe(subscription -> subscribed.countDown()).doOnCancel(cancelled::countDown));
        start(QaMode.DIRECT, Duration.ofSeconds(2));
        await(subscribed);
        emitter.completionCallback.run();
        await(cancelled);
        assertThat(emitter.names()).doesNotContain("done", "error");
        assertThat(deadlines.getQueue()).isEmpty();
    }

    @Test void absoluteDeadlineCancelsAnActiveStreamAndEmitsOnlyError() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.<ChatResponse>never().doOnCancel(cancelled::countDown));
        start(QaMode.DIRECT, Duration.ofMillis(250));
        await(emitter.finished); await(cancelled);
        assertThat(emitter.names()).contains("error").doesNotContain("done");
        assertThat(((Map<?, ?>) emitter.events.getLast().payload()).get("code")).isEqualTo("STREAM_TIMEOUT");
        assertThat(emitter.completions).isEqualTo(1);
        emitter.timeoutCallback.run();
        assertThat(emitter.completions).isEqualTo(1);
    }

    @Test void failedDeltaSendCancelsUpstreamAndNeverProducesDone() throws Exception {
        Sinks.Many<ChatResponse> source = Sinks.many().unicast().onBackpressureBuffer();
        CountDownLatch subscribed = new CountDownLatch(1), cancelled = new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenReturn(source.asFlux()
                .doOnSubscribe(subscription -> subscribed.countDown()).doOnCancel(cancelled::countDown));
        emitter.failEvent = "delta";
        start(QaMode.DIRECT, Duration.ofSeconds(2));
        await(subscribed);
        source.tryEmitNext(text("chunk"));
        await(cancelled); await(emitter.finished);
        assertThat(emitter.names()).doesNotContain("done");
        assertThat(deadlines.getQueue()).isEmpty();
        assertThat(dropped).isEmpty();
    }

    @Test void finalUsageOnlyChunkIsKeptInDoneWithoutAnEmptyDelta() throws Exception {
        ChatResponse usageOnly = new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(12, 4, 16)).build());
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(text("hello"), usageOnly));
        start(QaMode.DIRECT, Duration.ofSeconds(2));
        await(emitter.finished);
        assertThat(emitter.names()).containsExactly("status", "knowledge", "delta", "done");
        AnswerResponse result = (AnswerResponse) emitter.events.getLast().payload();
        assertThat(result.answer()).isEqualTo("hello");
        assertThat(result.usage().totalTokens()).isEqualTo(16);
        assertThat(result.timings().totalMs()).isEqualTo(result.latencyMs());
        assertThat(deadlines.getQueue()).isEmpty();
    }

    @Test void deadlineInterruptsPendingRetrievalBeforeAnyModelSubscription() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        when(python.retrieve(anyString(), anyList(), anyList())).thenAnswer(invocation -> {
            entered.countDown();
            try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            throw new IllegalStateException("retrieval stopped");
        });
        start(QaMode.RANKED, Duration.ofMillis(250));
        await(entered); await(emitter.finished); await(interrupted);
        verifyNoInteractions(model);
        assertThat(emitter.names()).containsExactly("status", "error");
    }

    @Test void cancellationBeforeSubscribeReturnsDisposesTheLateSubscription() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), cancelled = new CountDownLatch(1);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.create(sink -> {
            sink.onCancel(cancelled::countDown);
            entered.countDown();
            try { release.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }));
        start(QaMode.DIRECT, Duration.ofSeconds(2));
        await(entered);
        emitter.completionCallback.run();
        release.countDown();
        await(cancelled);
        assertThat(emitter.names()).doesNotContain("done", "error");
        assertThat(deadlines.getQueue()).isEmpty();
    }

    private static class RecordingEmitter extends SseEmitter {
        record Event(String name, Object payload) { }
        final List<Event> events = new CopyOnWriteArrayList<>();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile Runnable completionCallback, timeoutCallback;
        volatile Consumer<Throwable> errorCallback;
        volatile int completions;
        volatile String failEvent;
        @Override public void onCompletion(Runnable callback) { completionCallback = callback; }
        @Override public void onTimeout(Runnable callback) { timeoutCallback = callback; }
        @Override public void onError(Consumer<Throwable> callback) { errorCallback = callback; }
        @Override public void send(SseEventBuilder builder) throws IOException {
            String name = null; Object payload = null;
            for (var part : builder.build()) {
                if (part.getData() instanceof String framing && framing.startsWith("event:"))
                    name = framing.substring(6).split("\n")[0];
                else if (!(part.getData() instanceof String)) payload = part.getData();
            }
            if (name != null && name.equals(failEvent)) { finished.countDown(); throw new IOException("client disconnected"); }
            events.add(new Event(name, payload));
        }
        List<String> names() { return events.stream().map(Event::name).toList(); }
        @Override public void complete() { completions++; completionCallback.run(); finished.countDown(); }
        @Override public void completeWithError(Throwable error) {
            completions++; errorCallback.accept(error); finished.countDown();
        }
    }
}
