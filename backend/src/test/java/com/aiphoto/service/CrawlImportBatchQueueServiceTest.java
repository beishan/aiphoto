package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.repository.CrawlImportBatchRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlImportBatchQueueServiceTest {

    @Mock private CrawlImportBatchRepository batchRepository;

    @Test
    void onlyOneWorkerCanWinTheAtomicBatchClaim() {
        CrawlImportBatchQueueService queue = new CrawlImportBatchQueueService(batchRepository);
        when(batchRepository.claim(eq(12L), eq("worker-1"), any(), any())).thenReturn(1);
        when(batchRepository.claim(eq(12L), eq("worker-2"), any(), any())).thenReturn(0);

        assertThat(queue.claim(12L, "worker-1")).isTrue();
        assertThat(queue.claim(12L, "worker-2")).isFalse();
    }

    @Test
    void batchLeaseRenewalUsesARecoverableTenMinuteWindow() {
        CrawlImportBatchQueueService queue = new CrawlImportBatchQueueService(batchRepository);
        when(batchRepository.renewLease(eq(12L), eq("worker-1"), any())).thenReturn(1);
        ArgumentCaptor<LocalDateTime> until = ArgumentCaptor.forClass(LocalDateTime.class);

        assertThat(queue.renew(12L, "worker-1")).isTrue();

        verify(batchRepository).renewLease(eq(12L), eq("worker-1"), until.capture());
        assertThat(until.getValue()).isAfter(LocalDateTime.now().plusMinutes(9));
    }
}
