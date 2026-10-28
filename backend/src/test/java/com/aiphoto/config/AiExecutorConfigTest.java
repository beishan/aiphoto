package com.aiphoto.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class AiExecutorConfigTest {
    @Test
    void saturatedAiExecutorRunsTaskOnProducerAndRejectsAfterShutdown() throws Exception {
        var executor = new AiExecutorConfig().aiTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});
            var actualThread = new AtomicReference<Thread>();
            executor.execute(() -> actualThread.set(Thread.currentThread()));
            assertThat(actualThread.get()).isSameAs(Thread.currentThread());
        } finally {
            release.countDown();
            executor.shutdown();
        }
        assertThatThrownBy(() -> executor.execute(() -> {}))
                .isInstanceOf(TaskRejectedException.class);
    }
}
