package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.repository.CrawlJobRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlJobQueueServiceTest {

    @Mock private CrawlJobRepository jobRepository;
    @Mock private CrawlDiskGuard diskGuard;

    @Test
    void lowSpaceBlocksNewClaimsAndReturnsRunningJobsToTheQueue() {
        var queue = new CrawlJobQueueService(jobRepository, diskGuard);
        when(diskGuard.blocked()).thenReturn(true);

        assertThat(queue.claim(7L, "worker-1")).isFalse();
        assertThat(queue.renew(7L, "worker-1")).isFalse();

        verify(jobRepository).yieldForDiskSpace(7L, "worker-1");
        org.mockito.Mockito.verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void onlyReportsAClaimWhenTheAtomicUpdateWins() {
        CrawlJobQueueService queue = new CrawlJobQueueService(jobRepository, diskGuard);
        when(jobRepository.claim(eq(7L), eq("worker-1"), any(), any())).thenReturn(1);
        when(jobRepository.claim(eq(7L), eq("worker-2"), any(), any())).thenReturn(0);

        assertThat(queue.claim(7L, "worker-1")).isTrue();
        assertThat(queue.claim(7L, "worker-2")).isFalse();
    }

    @Test
    void renewalExtendsLeaseWellBeyondTheCurrentTime() {
        CrawlJobQueueService queue = new CrawlJobQueueService(jobRepository, diskGuard);
        when(jobRepository.renewLease(eq(7L), eq("worker-1"), any())).thenReturn(1);
        ArgumentCaptor<LocalDateTime> until = ArgumentCaptor.forClass(LocalDateTime.class);

        assertThat(queue.renew(7L, "worker-1")).isTrue();

        verify(jobRepository).renewLease(eq(7L), eq("worker-1"), until.capture());
        assertThat(until.getValue()).isAfter(LocalDateTime.now().plusMinutes(9));
    }
}
