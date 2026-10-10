package com.aiphoto.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CrawlerExecutorConfig {
    @Bean("crawlerTaskExecutor")
    public ThreadPoolTaskExecutor crawlerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("crawler-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
