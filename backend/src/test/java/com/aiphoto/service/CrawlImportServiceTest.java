package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlImportBatch;
import com.aiphoto.entity.CrawlImportItem;
import com.aiphoto.entity.Photo;
import com.aiphoto.repository.CrawlAssetRepository;
import com.aiphoto.repository.CrawlImportBatchRepository;
import com.aiphoto.repository.CrawlImportItemRepository;
import com.aiphoto.repository.PhotoRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlImportServiceTest {

    @Mock private CrawlService crawlService;
    @Mock private CrawlImportItemService itemService;
    @Mock private CrawlAssetRepository assetRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private CrawlImportBatchRepository batchRepository;
    @Mock private CrawlImportItemRepository importItemRepository;
    @Mock private CrawlImportBatchQueueService queueService;
    private CrawlImportService importService;

    @BeforeEach
    void setUp() {
        importService = new CrawlImportService(
                crawlService, itemService, assetRepository, photoRepository,
                batchRepository, importItemRepository, queueService);
    }

    @Test
    void asynchronousBatchContinuesAfterIndividualFailuresAndCountsEveryOutcome() throws Exception {
        Map<Long, CrawlImportItem> items = configurePersistedBatch(8L, 9L, "request-1");
        doAnswer(invocation -> switch (invocation.getArgument(0, Long.class).intValue()) {
            case 2 -> new CrawlImportItemService.ImportOutcome(CrawlImportItemService.Result.SKIPPED, 20L);
            case 3 -> throw new IllegalStateException("failed");
            default -> new CrawlImportItemService.ImportOutcome(CrawlImportItemService.Result.SUCCESS, 10L);
        }).when(itemService).importOne(anyLong(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyList());

        CrawlImportService.ImportResult result = importService.importAssets(
                8L, List.of(1L, 2L, 3L, 1L), 9L, null, List.of(), "request-1");
        ArgumentCaptor<CrawlImportBatch> batchCaptor = ArgumentCaptor.forClass(CrawlImportBatch.class);
        verify(batchRepository).saveAndFlush(batchCaptor.capture());
        CrawlImportBatch batch = batchCaptor.getValue();
        batch.setStatus(CrawlImportBatch.Status.RUNNING);
        when(queueService.renew(40L, "worker-1")).thenReturn(true);

        importService.processBatch(40L, "worker-1");

        assertThat(result.batchId()).isEqualTo(40L);
        assertThat(result.status()).isEqualTo(CrawlImportBatch.Status.QUEUED);
        assertThat(batch.getStatus()).isEqualTo(CrawlImportBatch.Status.PARTIAL);
        assertThat(batch.getSuccessCount()).isEqualTo(1);
        assertThat(batch.getSkippedCount()).isEqualTo(1);
        assertThat(batch.getFailCount()).isEqualTo(1);
        assertThat(items.get(3L).getErrorMessage()).isEqualTo("failed");
        verify(crawlService).getJob(8L, 9L);
    }

    @Test
    void completedIdempotentBatchReturnsStoredResultWithoutImportingAgain() throws Exception {
        CrawlImportBatch batch = batch(40L, 8L, 9L, "same-request", List.of(1L, 2L));
        batch.setStatus(CrawlImportBatch.Status.COMPLETED);
        batch.setSuccessCount(1);
        batch.setSkippedCount(1);
        when(batchRepository.findByOwnerIdAndIdempotencyKey(9L, "same-request"))
                .thenReturn(Optional.of(batch));
        when(importItemRepository.existsByBatchIdAndAssetId(40L, 1L)).thenReturn(true);
        when(importItemRepository.existsByBatchIdAndAssetId(40L, 2L)).thenReturn(true);

        CrawlImportService.ImportResult result = importService.importAssets(
                8L, List.of(1L, 2L), 9L, null, List.of(), "same-request");

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(itemService, never()).importOne(
                anyLong(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsReuseOfIdempotencyKeyForDifferentSelection() {
        CrawlImportBatch batch = batch(40L, 8L, 9L, "same-request", List.of(1L));
        when(batchRepository.findByOwnerIdAndIdempotencyKey(9L, "same-request"))
                .thenReturn(Optional.of(batch));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> importService.importAssets(
                        8L, List.of(2L), 9L, null, List.of(), "same-request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("幂等键");
    }

    @Test
    void previewsImportableDuplicatesAndBlockedItems() {
        CrawlAsset importable = asset(1L, CrawlAsset.Status.DOWNLOADED, "new");
        CrawlAsset duplicate = asset(2L, CrawlAsset.Status.DOWNLOADED, "existing");
        CrawlAsset deleted = asset(3L, CrawlAsset.Status.DELETED, "deleted");
        CrawlAsset imported = asset(4L, CrawlAsset.Status.IMPORTED, "imported");
        when(assetRepository.findByIdAndJobId(1L, 8L)).thenReturn(Optional.of(importable));
        when(assetRepository.findByIdAndJobId(2L, 8L)).thenReturn(Optional.of(duplicate));
        when(assetRepository.findByIdAndJobId(3L, 8L)).thenReturn(Optional.of(deleted));
        when(assetRepository.findByIdAndJobId(4L, 8L)).thenReturn(Optional.of(imported));
        when(assetRepository.findByIdAndJobId(5L, 8L)).thenReturn(Optional.empty());
        when(photoRepository.findByFileHashMd5("new")).thenReturn(Optional.empty());
        when(photoRepository.findByFileHashMd5("existing")).thenReturn(Optional.of(new Photo()));

        CrawlImportService.ImportPreview result = importService.preview(
                8L, List.of(1L, 2L, 3L, 4L, 5L, 1L), 9L);

        assertThat(result.requested()).isEqualTo(5);
        assertThat(result.importable()).isEqualTo(1);
        assertThat(result.duplicates()).isEqualTo(1);
        assertThat(result.alreadyImported()).isEqualTo(1);
        assertThat(result.blocked()).isEqualTo(2);
        verify(crawlService).getJob(8L, 9L);
    }

    private CrawlAsset asset(Long id, CrawlAsset.Status status, String hash) {
        CrawlAsset asset = new CrawlAsset();
        asset.setId(id);
        asset.setStatus(status);
        asset.setFileHashMd5(hash);
        return asset;
    }

    private Map<Long, CrawlImportItem> configurePersistedBatch(
            Long jobId, Long ownerId, String key) {
        Map<Long, CrawlImportItem> items = new LinkedHashMap<>();
        CrawlImportBatch[] storedBatch = new CrawlImportBatch[1];
        when(batchRepository.findByOwnerIdAndIdempotencyKey(ownerId, key))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            CrawlImportBatch batch = invocation.getArgument(0);
            batch.setId(40L);
            storedBatch[0] = batch;
            return batch;
        }).when(batchRepository).saveAndFlush(org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            CrawlImportBatch batch = invocation.getArgument(0);
            storedBatch[0] = batch;
            return batch;
        }).when(batchRepository).save(org.mockito.ArgumentMatchers.any());
        when(batchRepository.findById(40L)).thenAnswer(invocation -> Optional.of(storedBatch[0]));
        when(importItemRepository.existsByBatchIdAndAssetId(
                org.mockito.ArgumentMatchers.eq(40L), anyLong())).thenAnswer(
                invocation -> items.containsKey(invocation.getArgument(1, Long.class)));
        doAnswer(invocation -> {
            CrawlImportItem item = invocation.getArgument(0);
            if (item.getId() == null) item.setId((long) items.size() + 1);
            items.put(item.getAssetId(), item);
            return item;
        }).when(importItemRepository).save(org.mockito.ArgumentMatchers.any());
        when(importItemRepository.findByBatchIdOrderById(40L))
                .thenAnswer(invocation -> List.copyOf(items.values()));
        when(importItemRepository.countByBatchIdAndStatus(
                org.mockito.ArgumentMatchers.eq(40L), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> items.values().stream()
                        .filter(item -> item.getStatus() == invocation.getArgument(1)).count());
        return items;
    }

    private CrawlImportBatch batch(
            Long id, Long jobId, Long ownerId, String key, List<Long> assetIds) {
        CrawlImportBatch batch = new CrawlImportBatch();
        batch.setId(id);
        batch.setJobId(jobId);
        batch.setOwnerId(ownerId);
        batch.setIdempotencyKey(key);
        batch.setAssetIdsJson(assetIds.toString());
        batch.setTagIdsJson("[]");
        batch.setRequestedCount(assetIds.size());
        return batch;
    }
}
