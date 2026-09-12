package com.aiphoto.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.async.CrawlWorker;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.entity.CrawlSite;
import com.aiphoto.repository.CrawlJobRepository;
import com.aiphoto.repository.CrawlRuleRepository;
import com.aiphoto.repository.CrawlSiteRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlerScheduleServiceTest {

    @Mock private CrawlSiteRepository siteRepository;
    @Mock private CrawlRuleRepository ruleRepository;
    @Mock private CrawlJobRepository jobRepository;
    @Mock private CrawlService crawlService;
    @Mock private CrawlWorker crawlWorker;

    @Test
    void startsOneDueDailyDiscoveryJob() {
        CrawlSite site = new CrawlSite();
        site.setId(3L);
        site.setOwnerId(9L);
        site.setScheduleEnabled(true);
        site.setDailyScanTime(LocalTime.MIN);
        CrawlRule rule = new CrawlRule();
        rule.setId(4L);
        CrawlJob job = new CrawlJob();
        job.setId(5L);
        when(siteRepository.findByScheduleEnabledTrue()).thenReturn(List.of(site));
        when(jobRepository.existsBySiteIdAndStatusIn(any(), any())).thenReturn(false);
        when(ruleRepository.findBySiteIdAndOwnerIdAndEnabledTrue(3L, 9L))
                .thenReturn(Optional.of(rule));
        when(siteRepository.claimDailyScan(any(), any())).thenReturn(1);
        when(crawlService.createJob(4L, 9L)).thenReturn(job);
        var service = new CrawlerScheduleService(
                siteRepository, ruleRepository, jobRepository, crawlService, crawlWorker);

        service.dispatchDailyScans();

        verify(crawlWorker).discover(5L);
    }
}
