package edu.wfust.learning.platform.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Component
public class PythonGraphClient {
    private final RestClient client;

    @Autowired
    public PythonGraphClient(@Value("${python.knowledge.base-url:http://127.0.0.1:8999}") String baseUrl,
                             @Value("${python.knowledge.graph-connect-timeout:3s}") Duration connectTimeout,
                             @Value("${python.knowledge.graph-read-timeout:20s}") Duration readTimeout) {
        if (baseUrl == null || baseUrl.isBlank() || connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout == null || readTimeout.isZero() || readTimeout.isNegative())
            throw new IllegalArgumentException("Python graph client timeout configuration is invalid");
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public PythonGraphClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(20));
    }

    public PythonGraphResponse retrieve(String entity, int topK) {
        if (entity == null || entity.isBlank()) throw new IllegalArgumentException("entity must not be blank");
        PythonGraphRequest request = new PythonGraphRequest(entity, List.of(entity), List.of(), List.of(), null, 1, Math.min(200, Math.max(1, topK)));
        try {
            PythonGraphResponse response = client.post().uri("/api/knowledge/retrieve").body(request)
                    .retrieve().body(PythonGraphResponse.class);
            if (response == null || response.triples() == null
                    || response.triples().stream().anyMatch(triple -> triple == null || triple.subject() == null
                    || triple.predicate() == null || triple.object() == null || !Double.isFinite(triple.score())))
                throw unavailable(null);
            return response;
        } catch (KnowledgeGraphUnavailableException failure) { throw failure; }
        catch (RestClientException failure) { throw unavailable(failure); }
        catch (RuntimeException failure) { throw unavailable(failure); }
    }

    public boolean isAvailable() {
        try {
            return client.get().uri("/health")
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public record PythonGraphRequest(String question, List<String> entities, List<String> historyEntities,
                                     List<String> historyMessages, String relation, Integer spaceId, Integer topK) { }
    public record Triple(String subject, String predicate, String object, double score) { }
    public record PythonGraphResponse(String question, List<?> entities, List<Triple> triples, String prompt,
                                      boolean hasEvidence, String promptTemplate) { }

    private KnowledgeGraphUnavailableException unavailable(Throwable cause) {
        return new KnowledgeGraphUnavailableException("知识图谱服务暂不可用，请确认 Python Bridge 与图数据库已启动。", cause);
    }

    static final class KnowledgeGraphUnavailableException extends RestClientException {
        KnowledgeGraphUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
