package edu.wfust.learning.platform.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class PythonGraphClient {
    private final RestClient client;

    public PythonGraphClient(@Value("${python.knowledge.base-url:http://127.0.0.1:8999}") String baseUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(20));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public PythonGraphResponse retrieve(String entity, int topK) {
        PythonGraphRequest request = new PythonGraphRequest(entity, List.of(entity), List.of(), List.of(), null, 1, Math.min(200, Math.max(1, topK)));
        PythonGraphResponse response = client.post().uri("/api/knowledge/retrieve").body(request)
                .retrieve().body(PythonGraphResponse.class);
        if (response == null) throw new org.springframework.web.client.RestClientException("Python 图谱服务未返回结果");
        return response;
    }

    public record PythonGraphRequest(String question, List<String> entities, List<String> historyEntities,
                                     List<String> historyMessages, String relation, Integer spaceId, Integer topK) { }
    public record Triple(String subject, String predicate, String object, double score) { }
    public record PythonGraphResponse(String question, List<?> entities, List<Triple> triples, String prompt,
                                      boolean hasEvidence, String promptTemplate) { }
}
