package edu.wfust.learning.ai;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/** Owns every resource for one SSE response, including cancellation during prepare/subscribe races. */
final class AnswerStreamSession {
    private final AnswerService service;
    private final AnswerRequest request;
    private final SseEmitter emitter;
    private final ExecutorService workers;
    private final ScheduledExecutorService deadlines;
    private final Duration timeout;
    private final Disposable.Swap subscription = Disposables.swap();
    private final FutureTask<Void> work;
    private final long startedAt = System.nanoTime();
    private final StringBuilder answer = new StringBuilder();
    private ScheduledFuture<?> deadline;
    private volatile Thread workerThread;
    private boolean terminal;
    private AnswerService.PreparedAnswer prepared;
    private long generationStartedAt;
    private AnswerResponse.TokenUsage usage;

    AnswerStreamSession(AnswerService service, AnswerRequest request, SseEmitter emitter,
                        ExecutorService workers, ScheduledExecutorService deadlines, Duration timeout) {
        this.service = service;
        this.request = request;
        this.emitter = emitter;
        this.workers = workers;
        this.deadlines = deadlines;
        this.timeout = timeout;
        this.work = new FutureTask<>(() -> { run(); return null; });
    }

    synchronized void start() {
        emitter.onCompletion(this::cancel);
        emitter.onError(error -> cancel());
        emitter.onTimeout(this::timeout);
        try {
            deadline = deadlines.schedule(this::timeout, timeout.toMillis(), TimeUnit.MILLISECONDS);
            workers.execute(work);
        } catch (RejectedExecutionException rejected) {
            terminal = true;
            cleanup(true);
            throw new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "AI_BUSY", "AI 服务繁忙，请稍后重试。", true);
        }
    }

    private void run() {
        workerThread = Thread.currentThread();
        if (!send("status", Map.of("stage", request.mode() == QaMode.DIRECT ? "generating" : "retrieving"))) return;
        try {
            AnswerService.PreparedAnswer result = service.prepare(request);
            synchronized (this) {
                if (terminal) return;
                prepared = result;
                var evidence = prepared.knowledge();
                if (!send("knowledge", Map.of("entities", evidence == null ? List.of() : evidence.entities(),
                        "triples", evidence == null ? List.of() : evidence.triples(),
                        "hasEvidence", evidence != null && evidence.hasEvidence(), "provenance", prepared.provenance()))) return;
                generationStartedAt = System.nanoTime();
            }
            if (service.usesDedicatedRankedModel(result)) {
                AnswerResponse validated = service.executePrepared(result);
                synchronized (this) {
                    if (terminal) return;
                    if (validated.errorCode() != null || validated.answer().isBlank()) {
                        fail(new AiFailure(HttpStatus.SERVICE_UNAVAILABLE,
                                validated.errorCode() == null ? "ANSWER_EVIDENCE_MISMATCH" : validated.errorCode(),
                                validated.error() == null ? "模型回答未通过证据校验。" : validated.error(), true));
                        return;
                    }
                    if (!send("delta", Map.of("content", validated.answer()))) return;
                    if (!send("done", validated)) return;
                    terminal = true;
                    cleanup(false);
                    emitter.complete();
                }
                return;
            }
            // Swap is disposed before a late subscribe() return can install a live subscription.
            subscription.update(service.stream(result).subscribe(this::next, this::fail, this::complete));
        } catch (RuntimeException exception) { fail(exception); }
    }

    private synchronized void next(ChatResponse response) {
        if (terminal) return;
        AnswerResponse.TokenUsage chunkUsage = AnswerResponse.TokenUsage.from(response);
        if (chunkUsage != null) usage = chunkUsage;
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) return;
        String text = response.getResult().getOutput().getText();
        if (text != null && !text.isEmpty()) {
            answer.append(text);
            send("delta", Map.of("content", text));
        }
    }

    private synchronized void complete() {
        if (terminal) return;
        if (answer.toString().isBlank()) {
            fail(AiFailure.modelEmpty());
            return;
        }
        AnswerResponse result = service.completed(prepared, answer.toString(), AnswerService.elapsedMs(startedAt),
                AnswerService.elapsedMs(generationStartedAt), usage);
        if (!send("done", result)) return;
        terminal = true;
        cleanup(false);
        emitter.complete();
    }

    private synchronized void fail(Throwable exception) {
        if (terminal) return;
        AiFailure failure = exception instanceof AiFailure known ? known
                : prepared == null && request.mode() != QaMode.DIRECT ? AiFailure.knowledge(exception) : AiFailure.model(exception);
        if (!send("error", Map.of("code", failure.code(), "message", failure.getMessage(), "configured", failure.configured()))) return;
        terminal = true;
        cleanup(true);
        emitter.complete();
    }

    private void timeout() {
        fail(new AiFailure(HttpStatus.SERVICE_UNAVAILABLE, "STREAM_TIMEOUT", "AI 流式请求超时，请稍后重试。", true));
    }

    private synchronized boolean send(String name, Object payload) {
        if (terminal) return false;
        try {
            emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException disconnected) {
            terminal = true;
            cleanup(true);
            // Spring dispatches send IOExceptions to its error lifecycle; no second completion or event is sent.
            return false;
        }
    }

    private synchronized void cancel() {
        if (terminal) return;
        terminal = true;
        cleanup(true);
    }

    private void cleanup(boolean cancelWork) {
        subscription.dispose();
        if (deadline != null) deadline.cancel(false);
        if (cancelWork) work.cancel(Thread.currentThread() != workerThread);
    }
}
