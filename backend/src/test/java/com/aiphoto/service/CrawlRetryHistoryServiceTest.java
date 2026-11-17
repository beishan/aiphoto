package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlRetry;
import com.aiphoto.repository.CrawlRetryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrawlRetryHistoryServiceTest {
    @Test
    void recordsActualStartAndTerminalResultWithoutOverwritingDuringExecution() {
        var repository = mock(CrawlRetryRepository.class);
        var service = new CrawlRetryHistoryService(repository);
        CrawlRetry retry = new CrawlRetry();
        when(repository.findFirstByJobIdAndFinishedAtIsNullOrderByIdDesc(1L))
                .thenReturn(Optional.of(retry));
        service.started(1L);
        var started = retry.getStartedAt();
        service.started(1L);
        assertThat(retry.getStartedAt()).isEqualTo(started);
        CrawlJob job = new CrawlJob();
        job.setId(1L);
        job.setStatus(CrawlJob.Status.RUNNING);
        service.finish(job);
        assertThat(retry.getFinishedAt()).isNull();
        job.setStatus(CrawlJob.Status.PARTIAL);
        job.setErrorMessage("部分图片失败");
        service.finish(job);
        assertThat(retry.getResult()).isEqualTo("PARTIAL");
        assertThat(retry.getMessage()).isEqualTo("部分图片失败");
        assertThat(retry.getFinishedAt()).isNotNull();
    }
}
