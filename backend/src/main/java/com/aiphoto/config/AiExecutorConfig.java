package com.aiphoto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiExecutorConfig {
    @Bean(name = {"taskExecutor", "aiTaskExecutor"})
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        return executor("ai-task-", 2, 512);
    }

    @Bean
    public ThreadPoolTaskExecutor modelDownloadExecutor() {
        return executor("model-download-", 1, 16);
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