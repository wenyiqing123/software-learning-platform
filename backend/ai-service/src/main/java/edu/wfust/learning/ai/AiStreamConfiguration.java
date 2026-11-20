package edu.wfust.learning.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration(proxyBeanMethods = false)
public class AiStreamConfiguration {
    @Bean(name = "aiStreamWorkers", destroyMethod = "shutdownNow")
    ExecutorService aiStreamWorkers() {
        return new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
                Thread.ofPlatform().daemon(true).name("ai-stream-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }
    @Bean(name = "aiStreamDeadlines", destroyMethod = "shutdownNow")
    ScheduledExecutorService aiStreamDeadlines() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                Thread.ofPlatform().daemon(true).name("ai-deadline-", 0).factory());
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
}
