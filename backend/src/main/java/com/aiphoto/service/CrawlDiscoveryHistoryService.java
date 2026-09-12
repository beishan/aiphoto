package com.aiphoto.service;

import com.aiphoto.entity.CrawlPage;
import com.aiphoto.repository.CrawlPageRepository;
import com.aiphoto.repository.CrawlSeenPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlDiscoveryHistoryService {

    private final CrawlPageRepository pageRepository;
    private final CrawlSeenPageRepository seenPageRepository;

    @Transactional
    public boolean addIfNew(
            Long jobId, Long siteId, String url, String normalizedUrl, String urlHash) {
        if (pageRepository.findByJobIdAndUrlHash(jobId, urlHash).isPresent()) {
            return false;
        }
        if (seenPageRepository.insertIfAbsent(siteId, normalizedUrl, urlHash) == 0) {
            var seen = seenPageRepository.findBySiteIdAndUrlHash(siteId, urlHash).orElseThrow();
            if (!normalizedUrl.equals(seen.getNormalizedUrl())) {
                throw new IllegalStateException("详情页 URL 哈希冲突");
            }
            return false;
        }
        CrawlPage page = new CrawlPage();
        page.setJobId(jobId);
        page.setUrl(url);
        page.setNormalizedUrl(normalizedUrl);
        page.setUrlHash(urlHash);
        pageRepository.save(page);

        return true;
    }
}
