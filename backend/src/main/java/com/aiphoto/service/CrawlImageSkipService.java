package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlImageSkip;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlImageSkipRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlImageSkipService {
    @org.springframework.beans.factory.annotation.Autowired
    private CrawlStagingStorageService thumbnailStorage;

    @Transactional
    public byte[] thumbnail(Long id, Long ownerId) throws Exception {
        var skip = skipRepository.findByIdAndOwnerId(id, ownerId).orElseThrow();
        if (skip.getThumbnailPath() == null) {
            for (String source : assetRepository.skipThumbnailSources(ownerId, skip.getUrlHash(), org.springframework.data.domain.PageRequest.of(0, 5))) {
                try {
                    skip.setThumbnailPath(thumbnailStorage.writeSkipThumbnail(skip.getId(), thumbnailStorage.read(source)));
                    skipRepository.save(skip);
                    break;
                } catch (Exception ignored) { /* Try another existing local thumbnail; never fetch an original. */ }
            }
        }
        if (skip.getThumbnailPath() == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return thumbnailStorage.read(skip.getThumbnailPath());
    }

    private final CrawlService crawlService;
    private final CrawlAssetRepository assetRepository;
    private final CrawlImageSkipRepository skipRepository;

    public Page<CrawlImageSkip> list(Long ownerId, String query, Pageable pageable) {
        String value = query == null ? "" : query.trim();
        return value.isEmpty()
                ? skipRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable)
                : skipRepository.findByOwnerIdAndImageUrlContainingIgnoreCaseOrderByCreatedAtDesc(
                        ownerId, value, pageable);
    }

    @Transactional
    public int addFromAssets(Long jobId, List<Long> ids, Long ownerId, String reason) {
        crawlService.getJob(jobId, ownerId);
        List<Long> safeIds = ids == null ? List.of() : ids.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (safeIds.isEmpty()) return 0;
        int changed = 0;
        for (CrawlAsset asset : assetRepository.findByIdInAndJobId(safeIds, jobId)) {
            CrawlImageSkip skip = skipRepository
                    .findByOwnerIdAndUrlHash(ownerId, asset.getUrlHash())
                    .orElseGet(CrawlImageSkip::new);
            if (skip.getId() != null
                    && !asset.getNormalizedUrl().equals(skip.getNormalizedUrl())) {
                throw new IllegalStateException("图片 URL 哈希冲突");
            }
            skip.setOwnerId(ownerId);
            skip.setImageUrl(asset.getImageUrl());
            skip.setNormalizedUrl(asset.getNormalizedUrl());
            skip.setUrlHash(asset.getUrlHash());
            skip.setSourcePageUrl(asset.getSourcePageUrl());
            skip.setReason(reason == null || reason.isBlank() ? "用户标记为以后跳过" : reason.trim());
            skipRepository.save(skip);
            if (skip.getThumbnailPath() == null && (asset.getThumbnailPath() != null || asset.getLocalPath() != null)) {
                try {
                    byte[] thumbnail;
                    if (asset.getThumbnailPath() != null) thumbnail = thumbnailStorage.read(asset.getThumbnailPath());
                    else {
                        var output = new java.io.ByteArrayOutputStream();
                        net.coobird.thumbnailator.Thumbnails.of(new java.io.ByteArrayInputStream(thumbnailStorage.read(asset.getLocalPath())))
                                .size(400, 400).outputFormat("jpg").toOutputStream(output);
                        thumbnail = output.toByteArray();
                    }
                    skip.setThumbnailPath(thumbnailStorage.writeSkipThumbnail(skip.getId(), thumbnail));
                    skipRepository.save(skip);
                } catch (Exception e) { throw new IllegalStateException("保存跳过图片缩略图失败", e); }
            }
            changed++;
        }
        return changed;
    }

    @Transactional
    public void delete(Long id, Long ownerId) {
        CrawlImageSkip skip = skipRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("跳过规则不存在"));
        skipRepository.delete(skip);
    }
}
