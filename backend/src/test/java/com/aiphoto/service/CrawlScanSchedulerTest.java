package com.aiphoto.service;

import static org.mockito.Mockito.*;
import com.aiphoto.entity.CrawlScanPage;
import com.aiphoto.repository.CrawlScanPageRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrawlScanSchedulerTest {
    @Test void failingScanEntryDoesNotBlockOtherSitesOrEntries() {
        var repository = mock(CrawlScanPageRepository.class);
        var workflow = mock(CrawlWorkflowService.class);
        CrawlScanPage first = new CrawlScanPage(); first.setId(1L); first.setOwnerId(9L);
        CrawlScanPage second = new CrawlScanPage(); second.setId(2L); second.setOwnerId(9L);
        when(repository.findByAutoScanEnabledTrue()).thenReturn(List.of(first, second));
        when(workflow.scan(1L, 9L, true)).thenThrow(new IllegalStateException("规则尚未配置"));
        new CrawlScanScheduler(repository, workflow).dispatch();
        verify(workflow).scan(2L, 9L, true);
    }
}
