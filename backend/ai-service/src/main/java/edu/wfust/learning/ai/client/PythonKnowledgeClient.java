package edu.wfust.learning.ai.client;

import edu.wfust.learning.ai.AiFailure;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PythonKnowledgeClient implements AutoCloseable {
    private final RestClient client;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int maxResponseBytes;
    private final int maxUntruncatedTriples;

    public PythonKnowledgeClient(@Value("${python.knowledge.base-url:http://127.0.0.1:8999}") String baseUrl,
                                 @Value("${python.knowledge.connect-timeout:3s}") Duration connectTimeout,
                                 @Value("${python.knowledge.read-timeout:15s}") Duration readTimeout,
                                 @Value("${python.knowledge.max-response-bytes:2097152}") int maxResponseBytes,
                                 @Value("${python.knowledge.max-untruncated-triples:200}") int maxUntruncatedTriples,
                                 ObjectMapper objectMapper) {
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()
                || maxResponseBytes < 1 || maxResponseBytes > 16777216 || maxUntruncatedTriples < 1 || maxUntruncatedTriples > 5000)
            throw new IllegalArgumentException("Python knowledge timeout or response limit configuration is invalid");
        this.httpClient = createHttpClient(connectTimeout);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.maxResponseBytes = maxResponseBytes;
        this.maxUntruncatedTriples = maxUntruncatedTriples;
    }

    public PythonKnowledgeResponse retrieve(String question, List<String> entities, List<String> historyMessages) {
        return retrieve(question, entities, historyMessages, List.of());
    }

    public PythonKnowledgeResponse retrieve(String question, List<String> entities, List<String> historyMessages,
                                            List<String> historyEntities) {
        return retrieve(question, entities, historyMessages, historyEntities, 20);
    }

    /** The inspected Bridge contract explicitly preserves raw order for topK=0. No Java truncation is allowed. */
    public PythonKnowledgeResponse retrieveUntruncated(String question) {
        return retrieve(question, List.of(), List.of(), List.of(), 0);
    }

    private PythonKnowledgeResponse retrieve(String question, List<String> entities, List<String> historyMessages,
                                             List<String> historyEntities, int topK) {
        PythonKnowledgeRequest request = new PythonKnowledgeRequest(question,
                entities == null ? List.of() : entities, historyEntities == null ? List.of() : historyEntities,
                historyMessages == null ? List.of() : historyMessages, null, 1, topK);
        try {
            byte[] bytes = client.post().uri("/api/knowledge/retrieve").body(request)
                    .retrieve().body(byte[].class);
            if (bytes == null || bytes.length == 0) throw unavailable(null);
            if (bytes.length > maxResponseBytes) {
                if (topK == 0) throw AiFailure.ablationLimit();
                throw unavailable(null);
            }
            PythonKnowledgeResponse knowledge = objectMapper.readValue(bytes, PythonKnowledgeResponse.class);
            if (!question.equals(knowledge.question())
                    || knowledge.hasEvidence() != !knowledge.triples().isEmpty()
                    || knowledge.triples().stream().anyMatch(triple -> triple.subject() == null
                    || triple.predicate() == null || triple.object() == null || !Double.isFinite(triple.score())))
                throw unavailable(null);
            if (topK == 0 && knowledge.triples().size() > maxUntruncatedTriples) throw AiFailure.ablationLimit();
            return knowledge;
        } catch (AiFailure | KnowledgeServiceUnavailableException failure) { throw failure; }
        catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause())
                if (cause instanceof AiFailure failure) throw failure;
            throw unavailable(exception);
        }
    }

    public boolean isAvailable() {
        try { return client.get().uri("/health").exchange((request, response) -> response.getStatusCode().is2xxSuccessful()); }
        catch (RuntimeException exception) { return false; }
    }
    static HttpClient createHttpClient(Duration connectTimeout) {
        // The existing Uvicorn bridge accepts HTTP/1.1, not the JDK client's h2c upgrade.
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build();
    }
    private KnowledgeServiceUnavailableException unavailable(Throwable cause) {
        return new KnowledgeServiceUnavailableException("知识检索服务暂不可用或响应不符合契约，请稍后重试。", cause);
    }
    @PreDestroy @Override public void close() { httpClient.shutdownNow(); }
}
