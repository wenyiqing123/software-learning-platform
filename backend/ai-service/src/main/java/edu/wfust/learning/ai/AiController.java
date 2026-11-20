package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.PythonKnowledgeClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

@RestController
@RequestMapping("/api/ai")
@Validated
public class AiController {
    private final AnswerService answerService;
    private final PythonKnowledgeClient knowledgeClient;
    private final ExecutorService workers;
    private final ScheduledExecutorService deadlines;
    private final Duration streamTimeout;

    public AiController(AnswerService answerService, PythonKnowledgeClient knowledgeClient,
                        @Qualifier("aiStreamWorkers") ExecutorService workers,
                        @Qualifier("aiStreamDeadlines") ScheduledExecutorService deadlines,
                        @Value("${ai.stream.timeout:120s}") Duration streamTimeout) {
        if (streamTimeout.toMillis() < 1 || streamTimeout.compareTo(Duration.ofMinutes(10)) > 0)
            throw new IllegalArgumentException("AI stream timeout must be between 1ms and 10 minutes");
        this.answerService = answerService;
        this.knowledgeClient = knowledgeClient;
        this.workers = workers;
        this.deadlines = deadlines;
        this.streamTimeout = streamTimeout;
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody AnswerRequest request) { return answerService.answer(request); }

    @PostMapping("/compare")
    public List<AnswerResponse> compare(@Valid @RequestBody CompareRequest request) {
        return answerService.compare(request.question(), Boolean.TRUE.equals(request.includeAblations()));
    }

    @PostMapping("/compare/qwen-matrix")
    public List<AnswerResponse> compareQwenMatrix(@Valid @RequestBody CompareMatrixRequest request) {
        return answerService.compareQwenMatrix(request.question());
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AnswerRequest request) {
        answerService.requireInteractive(request);
        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        new AnswerStreamSession(answerService, request, emitter, workers, deadlines, streamTimeout).start();
        return emitter;
    }

    @GetMapping("/dependencies")
    public Map<String, Object> dependencies() {
        ModelSettings settings = answerService.settings();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", settings.configured());
        result.put("model", settings.model());
        result.put("temperature", settings.temperature());
        result.put("maxTokens", settings.maxTokens());
        result.put("topK", 20);
        result.put("configurationError", settings.configurationError());
        result.putAll(answerService.rankedModelInfo());
        Boolean pythonAvailable = settings.configured() ? knowledgeClient.isAvailable() : null;
        result.put("pythonKnowledgeService", pythonAvailable);
        result.put("pythonKnowledgeStatus", pythonAvailable == null ? "NOT_CHECKED" : pythonAvailable ? "UP" : "DOWN");
        return result;
    }

    /** Local startup probe; never includes model URLs, keys, or user data. */
    @GetMapping("/matrix-health")
    public Map<String, Object> matrixHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> info = answerService.rankedModelInfo();
        boolean enabled = Boolean.TRUE.equals(info.get("qwenMatrixEnabled"));
        boolean configured = Boolean.TRUE.equals(info.get("qwenMatrixConfigured"));
        result.put("status", enabled && configured ? "UP" : "DOWN");
        result.put("qwenMatrixEnabled", enabled);
        result.put("qwenMatrixConfigured", configured);
        result.put("qwenMatrixBaseModel", info.get("qwenMatrixBaseModel"));
        result.put("qwenMatrixAdapterModel", info.get("qwenMatrixAdapterModel"));
        return result;
    }

    public record CompareRequest(@NotBlank @Size(max = 2000) String question, Boolean includeAblations) { }
    public record CompareMatrixRequest(@NotBlank @Size(max = 2000) String question) { }
}
