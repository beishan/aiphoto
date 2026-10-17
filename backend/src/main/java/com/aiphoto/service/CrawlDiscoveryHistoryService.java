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
            Long jobId, Long siteId, String url, String normalizedUrl, String urlHash, String name) {
        String pageName = normalizeName(name);
        if (pageRepository.findByJobIdAndUrlHash(jobId, urlHash).isPresent()) {
            fillMissingNames(siteId, normalizedUrl, urlHash, pageName);
            return false;
        }
        if (seenPageRepository.insertIfAbsent(siteId, normalizedUrl, urlHash) == 0) {
            var seen = seenPageRepository.findBySiteIdAndUrlHash(siteId, urlHash).orElseThrow();
            if (!normalizedUrl.equals(seen.getNormalizedUrl())) {
                throw new IllegalStateException("详情页 URL 哈希冲突");
            }
            fillMissingNames(siteId, normalizedUrl, urlHash, pageName);
            return false;
        }
        CrawlPage page = new CrawlPage();
        page.setJobId(jobId);
        page.setUrl(url);
        page.setNormalizedUrl(normalizedUrl);
        page.setUrlHash(urlHash);
        page.setName(pageName);
        pageRepository.save(page);

        return true;
    }

    private void fillMissingNames(Long siteId, String normalizedUrl, String urlHash, String name) {
        if (name != null) pageRepository.fillMissingNames(siteId, normalizedUrl, urlHash, name);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.strip().replaceAll("\\s+", " ");
        int characters = normalized.codePointCount(0, normalized.length());
        return characters > 1000 ? normalized.substring(0, normalized.offsetByCodePoints(0, 1000)) : normalized;
    }
}
