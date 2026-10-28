package com.aiphoto.config;

import java.util.concurrent.RejectedExecutionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiExecutorConfig {
    @Bean(name = {"taskExecutor", "aiTaskExecutor"})
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = executor("ai-task-", 2, 512);
        // Slow producers when indexing is saturated instead of rejecting saved photos.
        executor.setRejectedExecutionHandler((task, pool) -> {
            if (pool.isShutdown()) {
                throw new RejectedExecutionException("AI task executor is shutting down");
            }
            task.run();
        });
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor modelDownloadExecutor() {
        return executor("model-download-", 1, 16);
    }

    @Bean
    public ThreadPoolTaskExecutor crawlImportExecutor() {
        return executor("crawl-import-", 2, 100);
    }

    private ThreadPoolTaskExecutor executor(String prefix, int workers, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}