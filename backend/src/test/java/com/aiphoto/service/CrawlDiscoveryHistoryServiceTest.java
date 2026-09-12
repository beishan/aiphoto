package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlPage;
import com.aiphoto.entity.CrawlSeenPage;
import com.aiphoto.repository.CrawlPageRepository;
import com.aiphoto.repository.CrawlSeenPageRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlDiscoveryHistoryServiceTest {

    @Mock private CrawlPageRepository pageRepository;
    @Mock private CrawlSeenPageRepository seenPageRepository;

    @Test
    void recordsOnlyAPreviouslyUnseenLink() {
        when(pageRepository.findByJobIdAndUrlHash(5L, "hash")).thenReturn(Optional.empty());
        when(seenPageRepository.insertIfAbsent(2L, "https://example.com/post/1", "hash"))
                .thenReturn(1);
        var service = new CrawlDiscoveryHistoryService(pageRepository, seenPageRepository);

        boolean added = service.addIfNew(
                5L, 2L, "https://example.com/post/1", "https://example.com/post/1", "hash");

        assertThat(added).isTrue();
        ArgumentCaptor<CrawlPage> page = ArgumentCaptor.forClass(CrawlPage.class);
        verify(pageRepository).save(page.capture());
        assertThat(page.getValue().getJobId()).isEqualTo(5L);
        assertThat(page.getValue().getNormalizedUrl()).isEqualTo("https://example.com/post/1");
    }

    @Test
    void doesNotAddALinkSeenByAnEarlierJob() {
        when(pageRepository.findByJobIdAndUrlHash(5L, "hash")).thenReturn(Optional.empty());
        when(seenPageRepository.insertIfAbsent(2L, "https://example.com/post/1", "hash"))
                .thenReturn(0);
        CrawlSeenPage seen = new CrawlSeenPage();
        seen.setNormalizedUrl("https://example.com/post/1");
        when(seenPageRepository.findBySiteIdAndUrlHash(2L, "hash"))
                .thenReturn(Optional.of(seen));
        var service = new CrawlDiscoveryHistoryService(pageRepository, seenPageRepository);

        boolean added = service.addIfNew(
                5L, 2L, "https://example.com/post/1", "https://example.com/post/1", "hash");

        assertThat(added).isFalse();
        verify(pageRepository, never()).save(any());
    }
}
