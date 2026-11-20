package edu.wfust.learning.platform.experiment;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
class AiComparisonClientTest {
    @Test void forwardsUserAuthorizationAndOnlyPersistsServerResponse() throws Exception {
        var auth=new AtomicReference<String>();var body=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/ai/compare",e->{auth.set(e.getRequestHeaders().getFirst("Authorization"));body.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] payload="[{\"question\":\"UML\",\"mode\":\"DIRECT\",\"answer\":\"统一建模语言\",\"error\":null}]".getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,payload.length);e.getResponseBody().write(payload);e.close();});
        server.start();try{var results=new AiComparisonClient("http://127.0.0.1:"+server.getAddress().getPort()).compare("UML",false,"Bearer session-token");assertThat(auth.get()).isEqualTo("Bearer session-token");assertThat(body.get()).contains("UML").doesNotContain("results");assertThat(results).singleElement().satisfies(r->assertThat(r.get("answer")).isEqualTo("统一建模语言"));}finally{server.stop(0);}
    }
}
