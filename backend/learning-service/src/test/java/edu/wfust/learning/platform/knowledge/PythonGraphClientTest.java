package edu.wfust.learning.platform.knowledge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class PythonGraphClientTest {
    @Test void sendsOnlyExistingReadContractAndClampsTopK() throws Exception {
        var request = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/knowledge/retrieve", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"question\":\"UML\",\"entities\":[],\"triples\":[{\"subject\":\"UML\",\"predicate\":\"包含\",\"object\":\"类图\",\"score\":0.9}],\"prompt\":\"原始提示\",\"hasEvidence\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var response = new PythonGraphClient("http://127.0.0.1:" + server.getAddress().getPort()).retrieve("UML", 500);
            var payload = new ObjectMapper().readTree(request.get());
            assertThat(payload.path("spaceId").asInt()).isEqualTo(1);
            assertThat(payload.path("topK").asInt()).isEqualTo(200);
            assertThat(payload.path("entities").get(0).asText()).isEqualTo("UML");
            assertThat(response.triples()).hasSize(1);
        } finally { server.stop(0); }
    }

    @Test void rejectsEmptyDependencyResponseAsRestClientFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/knowledge/retrieve", exchange -> { exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        try {
            var client = new PythonGraphClient("http://127.0.0.1:" + server.getAddress().getPort());
            assertThatThrownBy(() -> client.retrieve("UML", 20)).isInstanceOf(RestClientException.class);
        } finally { server.stop(0); }
    }
}
