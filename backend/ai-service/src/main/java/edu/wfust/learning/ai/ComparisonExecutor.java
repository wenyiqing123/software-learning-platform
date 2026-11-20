package edu.wfust.learning.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.concurrent.*;

/** Bounds both total comparison wall time and the number of upstream tasks retained by the service. */
@Component
public final class ComparisonExecutor implements AutoCloseable {
    private final Duration timeout;
    private final ThreadPoolExecutor executor;

    public ComparisonExecutor(@Value("${ai.compare.timeout:150s}") Duration timeout) {
        if (timeout == null || timeout.toMillis() < 1 || timeout.compareTo(Duration.ofSeconds(180)) > 0)
            throw new IllegalArgumentException("AI compare timeout must be between 1ms and 180 seconds");
        this.timeout = timeout;
        executor = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
                Thread.ofPlatform().daemon(true).name("ai-compare-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }
    long deadline() { return System.nanoTime() + timeout.toNanos(); }
    boolean expired(long deadline) { return deadline - System.nanoTime() <= 0; }
    <T> Future<T> submit(Callable<T> task) {
        try { return executor.submit(task); }
        catch (RejectedExecutionException rejected) {
            throw new AiFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_BUSY", "AI 服务繁忙，请稍后重试。", true);
        }
    }
    <T> T await(Future<T> future, long deadline) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    }
    void cancel(Future<?> task) {
        if (task == null || task.isDone()) return;
        task.cancel(true);
        if (task instanceof Runnable queued) executor.remove(queued);
    }
    @PreDestroy @Override public void close() { executor.shutdownNow(); }
}
