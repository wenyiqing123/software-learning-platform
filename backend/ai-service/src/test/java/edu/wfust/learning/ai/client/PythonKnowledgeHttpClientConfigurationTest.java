package edu.wfust.learning.ai.client;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.net.http.HttpClient;
import static org.assertj.core.api.Assertions.assertThat;

class PythonKnowledgeHttpClientConfigurationTest {
    @Test void pinsHttp11ForTheExistingUvicornBridge() {
        assertThat(PythonKnowledgeClient.createHttpClient(Duration.ofSeconds(1)).version())
                .isEqualTo(HttpClient.Version.HTTP_1_1);
    }
}
