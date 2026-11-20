package edu.wfust.learning.ai;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Function;

final class HttpStub implements AutoCloseable {
    record Request(String method, String path, String body) { }
    record Reply(int status, String body, long delayMs) { }
    final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    volatile Function<Request, Reply> responder = request -> new Reply(200, "{}", 0);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpServer server;
    HttpStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requests.add(request);
            Reply reply = responder.apply(request);
            try {
                if (reply.delayMs() > 0) Thread.sleep(reply.delayMs());
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException disconnected) {
                // Timeout and cancellation tests deliberately disconnect the caller.
            } finally { exchange.close(); }
        });
        server.start();
    }
    String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    Request takeRequest() throws InterruptedException {
        Request request = requests.poll(3, TimeUnit.SECONDS);
        if (request == null) throw new AssertionError("No HTTP request reached the test fixture");
        return request;
    }
    @Override public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
