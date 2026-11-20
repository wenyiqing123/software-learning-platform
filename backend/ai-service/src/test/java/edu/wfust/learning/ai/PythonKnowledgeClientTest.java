package edu.wfust.learning.ai;

import edu.wfust.learning.ai.client.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PythonKnowledgeClientTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private PythonKnowledgeClient client(HttpStub http, int maxBytes, int maxRawTriples) {
        return client(http, maxBytes, maxRawTriples, Duration.ofSeconds(3));
    }
    private PythonKnowledgeClient client(HttpStub http, int maxBytes, int maxRawTriples, Duration readTimeout) {
        return new PythonKnowledgeClient(http.baseUrl(), Duration.ofSeconds(1), readTimeout,
                maxBytes, maxRawTriples, json);
    }
    private String response(int count) {
        return json.writeValueAsString(new PythonKnowledgeResponse("question", List.of(),
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> new PythonTriple("s" + index, "p", "o", 0.1 + index)).toList(),
                "ORIGINAL\n  ", count > 0, "make_qa4ft.prompt_template"));
    }

    @Test void sendsReadOnlyFixedSpaceRequestAndTopKZeroOnlyForRawRetrieval() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200, response(2), 0);
            PythonKnowledgeClient client = client(http, 4096, 200);
            assertThat(client.retrieve("question", List.of(), List.of()).prompt()).isEqualTo("ORIGINAL\n  ");
            var normal = http.takeRequest();
            assertThat(normal.method()).isEqualTo("POST");
            assertThat(normal.path()).isEqualTo("/api/knowledge/retrieve");
            assertThat(json.readTree(normal.body()).path("spaceId").asInt()).isEqualTo(1);
            assertThat(json.readTree(normal.body()).path("topK").asInt()).isEqualTo(20);
            assertThat(client.retrieveUntruncated("question").triples()).extracting(PythonTriple::subject)
                    .containsExactly("s0", "s1");
            var raw = json.readTree(http.takeRequest().body());
            assertThat(raw.path("spaceId").asInt()).isEqualTo(1);
            assertThat(raw.path("topK").asInt()).isZero();
        }
    }

    @Test void forwardsFollowupHistoryInTheCorrectPythonFields() throws Exception {
        try (HttpStub http = new HttpStub(); PythonKnowledgeClient client = client(http, 4096, 200)) {
            http.responder = request -> new HttpStub.Reply(200, response(0), 0);
            client.retrieve("question", List.of(), List.of("prior question"), List.of("prior entity"));
            var body = json.readTree(http.takeRequest().body());
            assertThat(body.path("entities").size()).isZero();
            assertThat(body.path("historyMessages").get(0).asText()).isEqualTo("prior question");
            assertThat(body.path("historyEntities").get(0).asText()).isEqualTo("prior entity");
        }
    }

    @Test void failureBodiesAndMalformedResponsesBecomeSafeKnowledgeErrors() throws Exception {
        try (HttpStub http = new HttpStub()) {
            PythonKnowledgeClient client = client(http, 4096, 200);
            for (HttpStub.Reply reply : List.of(new HttpStub.Reply(503, "Bearer sk-sensitive", 0),
                    new HttpStub.Reply(200, "not-json sk-sensitive", 0),
                    new HttpStub.Reply(200, "{\"question\":\"different\"}", 0))) {
                http.responder = request -> reply;
                assertThatThrownBy(() -> client.retrieve("question", List.of(), List.of()))
                        .isInstanceOf(KnowledgeServiceUnavailableException.class).hasMessageNotContaining("sk-sensitive");
            }
        }
    }

    @Test void rawOrderAblationFailsOnTripleCountLimit() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200, response(3), 0);
            assertThatThrownBy(() -> client(http, 4096, 2).retrieveUntruncated("question"))
                    .isInstanceOf(AiFailure.class).hasMessageContaining("消融");
        }
    }

    @Test void rawOrderAblationFailsOnResponseByteLimit() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200, response(3), 0);
            assertThatThrownBy(() -> client(http, 64, 200).retrieveUntruncated("question"))
                    .isInstanceOf(AiFailure.class).hasMessageContaining("消融");
        }
    }

    @Test void knowledgeHttpTimeoutIsBounded() throws Exception {
        try (HttpStub http = new HttpStub()) {
            http.responder = request -> new HttpStub.Reply(200, response(0), 1500);
            assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                    assertThatThrownBy(() -> client(http, 4096, 200, Duration.ofMillis(150)).retrieve("question", List.of(), List.of()))
                            .isInstanceOf(KnowledgeServiceUnavailableException.class));
        }
    }
}
