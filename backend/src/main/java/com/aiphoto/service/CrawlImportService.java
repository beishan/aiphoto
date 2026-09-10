package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlImportBatch;
import com.aiphoto.entity.CrawlImportItem;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlImportBatchRepository;
import com.aiphoto.repository.CrawlImportItemRepository;
import com.aiphoto.repository.PhotoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlImportService {

    private final CrawlService crawlService;
    private final CrawlImportItemService itemService;
    private final CrawlAssetRepository assetRepository;
    private final PhotoRepository photoRepository;
    private final CrawlImportBatchRepository batchRepository;
    private final CrawlImportItemRepository importItemRepository;
    private final CrawlImportBatchQueueService queueService;

    public ImportPreview preview(Long jobId, List<Long> assetIds, Long ownerId) {
        crawlService.getJob(jobId, ownerId);
        int importable = 0;
        int duplicates = 0;
        int alreadyImported = 0;
        int blocked = 0;
        List<Long> ids = safeDistinctIds(assetIds);
        for (Long assetId : ids) {
            var asset = assetRepository.findByIdAndJobId(assetId, jobId);
            if (asset.isEmpty()) blocked++;
            else if (asset.get().getStatus() == CrawlAsset.Status.IMPORTED) alreadyImported++;
            else if (asset.get().getStatus() != CrawlAsset.Status.DOWNLOADED) blocked++;
            else if (photoRepository.findByFileHashMd5(asset.get().getFileHashMd5()).isPresent()) duplicates++;
            else importable++;
        }
        return new ImportPreview(ids.size(), importable, duplicates, alreadyImported, blocked);
    }

    @Transactional
    public ImportResult importAssets(
            Long jobId, List<Long> assetIds, Long ownerId, Long albumId,
            List<Long> tagIds, String idempotencyKey) {
        crawlService.getJob(jobId, ownerId);
        List<Long> ids = safeDistinctIds(assetIds);
        if (ids.isEmpty()) throw new IllegalArgumentException("请选择要入库的图片");
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        if (key.length() > 100) throw new IllegalArgumentException("幂等键长度不能超过 100");
        batchRepository.lockIdempotencyKey(ownerId + ":" + key);
        CrawlImportBatch batch = findOrCreateBatch(jobId, ids, ownerId, albumId, tagIds, key);
        if (!batch.getJobId().equals(jobId)) {
            throw new IllegalArgumentException("幂等键已用于其他采集任务");
        }
        validateSnapshot(batch, ids, albumId, tagIds);
        ensureItems(batch.getId(), ids);
        return result(batch);
    }

    public void processBatch(Long batchId, String workerId) {
        CrawlImportBatch batch = batchRepository.findById(batchId).orElseThrow();
        if (batch.getStatus() != CrawlImportBatch.Status.RUNNING) return;
        processPendingItems(
                batch, batch.getOwnerId(), batch.getAlbumId(),
                parseIds(batch.getTagIdsJson()), workerId);
    }

    public BatchView getBatch(Long batchId, Long ownerId) {
        return view(batchRepository.findByIdAndOwnerId(batchId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("导入批次不存在")));
    }

    public Page<CrawlImportItem> getBatchItems(
            Long batchId, Long ownerId, CrawlImportItem.Status status, Pageable pageable) {
        batchRepository.findByIdAndOwnerId(batchId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("导入批次不存在"));
        return status == null
                ? importItemRepository.findByBatchIdOrderById(batchId, pageable)
                : importItemRepository.findByBatchIdAndStatusOrderById(batchId, status, pageable);
    }

    private CrawlImportBatch findOrCreateBatch(
            Long jobId, List<Long> ids, Long ownerId, Long albumId,
            List<Long> tagIds, String key) {
        var existing = batchRepository.findByOwnerIdAndIdempotencyKey(ownerId, key);
        if (existing.isPresent()) return existing.get();
        CrawlImportBatch batch = new CrawlImportBatch();
        batch.setOwnerId(ownerId);
        batch.setJobId(jobId);
        batch.setIdempotencyKey(key);
        batch.setRequestedCount(ids.size());
        batch.setAlbumId(albumId);
        batch.setAssetIdsJson(ids.toString());
        batch.setTagIdsJson((tagIds == null ? List.of() : tagIds.stream().distinct().toList()).toString());
        return batchRepository.saveAndFlush(batch);
    }

    private void validateSnapshot(
            CrawlImportBatch batch, List<Long> ids, Long albumId, List<Long> tagIds) {
        String tags = (tagIds == null ? List.of() : tagIds.stream().distinct().toList()).toString();
        if (!batch.getAssetIdsJson().equals(ids.toString())
                || !java.util.Objects.equals(batch.getAlbumId(), albumId)
                || !java.util.Objects.equals(batch.getTagIdsJson(), tags)) {
            throw new IllegalArgumentException("幂等键已用于不同的入库内容或整理设置");
        }
    }

    private void ensureItems(Long batchId, List<Long> ids) {
        for (Long assetId : ids) {
            if (importItemRepository.existsByBatchIdAndAssetId(batchId, assetId)) continue;
            CrawlImportItem item = new CrawlImportItem();
            item.setBatchId(batchId);
            item.setAssetId(assetId);
            importItemRepository.save(item);
        }
        importItemRepository.flush();
    }

    private List<Long> parseIds(String json) {
        if (json == null || json.length() < 2) return List.of();
        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) return List.of();
        return java.util.Arrays.stream(content.split(","))
                .map(String::trim).map(Long::valueOf).toList();
    }

    private void processPendingItems(
            CrawlImportBatch batch, Long ownerId, Long albumId, List<Long> tagIds,
            String workerId) {
        for (CrawlImportItem item : importItemRepository.findByBatchIdOrderById(batch.getId())) {
            if (item.getStatus() != CrawlImportItem.Status.PENDING
                    && item.getStatus() != CrawlImportItem.Status.RUNNING) continue;
            if (!queueService.renew(batch.getId(), workerId)) return;
            item.setStatus(CrawlImportItem.Status.RUNNING);
            item.setErrorMessage(null);
            importItemRepository.save(item);
            try {
                CrawlImportItemService.ImportOutcome outcome = itemService.importOne(
                        item.getAssetId(), batch.getJobId(), ownerId, albumId, tagIds);
                item.setPhotoId(outcome.photoId());
                item.setStatus(outcome.result() == CrawlImportItemService.Result.SKIPPED
                        ? CrawlImportItem.Status.SKIPPED : CrawlImportItem.Status.SUCCESS);
            } catch (Exception exception) {
                item.setStatus(CrawlImportItem.Status.FAILED);
                item.setErrorMessage(shortMessage(exception));
            }
            importItemRepository.save(item);
        }
        finishBatch(batch);
    }

    private void finishBatch(CrawlImportBatch batch) {
        int success = count(batch.getId(), CrawlImportItem.Status.SUCCESS);
        int fail = count(batch.getId(), CrawlImportItem.Status.FAILED);
        int skipped = count(batch.getId(), CrawlImportItem.Status.SKIPPED);
        assetRepository.clearSimilarityGroups(batch.getJobId());
        batch.setSuccessCount(success);
        batch.setFailCount(fail);
        batch.setSkippedCount(skipped);
        batch.setStatus(fail == 0 ? CrawlImportBatch.Status.COMPLETED
                : success + skipped > 0 ? CrawlImportBatch.Status.PARTIAL
                : CrawlImportBatch.Status.FAILED);
        batch.setFinishedAt(LocalDateTime.now());
        batch.setLeaseOwner(null);
        batch.setLeaseUntil(null);
        batchRepository.save(batch);
    }

    private int count(Long batchId, CrawlImportItem.Status status) {
        return Math.toIntExact(importItemRepository.countByBatchIdAndStatus(batchId, status));
    }

    private ImportResult result(CrawlImportBatch batch) {
        return new ImportResult(batch.getId(), batch.getStatus(), batch.getSuccessCount(),
                batch.getFailCount(), batch.getSkippedCount());
    }

    private BatchView view(CrawlImportBatch batch) {
        return new BatchView(batch.getId(), batch.getJobId(), batch.getStatus(), batch.getRequestedCount(),
                batch.getSuccessCount(), batch.getFailCount(), batch.getSkippedCount(),
                batch.getAlbumId(), batch.getTagIdsJson(), batch.getCreatedAt(), batch.getFinishedAt());
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.substring(0, Math.min(message.length(), 1000));
    }

    private List<Long> safeDistinctIds(List<Long> assetIds) {
        return assetIds == null ? List.of() : assetIds.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    public record ImportResult(
            Long batchId, CrawlImportBatch.Status status, int success, int fail, int skipped) {}
    public record ImportPreview(
            int requested, int importable, int duplicates, int alreadyImported, int blocked) {}
    public record BatchView(
            Long id, Long jobId, CrawlImportBatch.Status status, int requested,
            int success, int fail, int skipped, Long albumId, String tagIdsJson,
            LocalDateTime createdAt, LocalDateTime finishedAt) {}
}
