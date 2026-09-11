package com.aiphoto.controller;

import com.aiphoto.async.CrawlWorker;
import com.aiphoto.async.CrawlImportWorker;
import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.entity.CrawlImportItem;
import com.aiphoto.entity.CrawlJob;
import com.aiphoto.entity.CrawlPage;
import com.aiphoto.entity.CrawlRule;
import com.aiphoto.entity.CrawlSite;
import com.aiphoto.entity.User;
import com.aiphoto.repository.UserRepository;
import com.aiphoto.service.CrawlImportService;
import com.aiphoto.service.CrawlService;
import com.aiphoto.service.CrawlSimilarityService;
import com.aiphoto.service.CrawlStagingStorageService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;
    private final CrawlWorker crawlWorker;
    private final CrawlImportWorker importWorker;
    private final CrawlImportService importService;
    private final CrawlStagingStorageService stagingStorage;
    private final CrawlSimilarityService similarityService;
    private final UserRepository userRepository;

    @GetMapping("/sites")
    public List<CrawlSite> listSites(Authentication authentication) {
        return crawlService.listSites(userId(authentication));
    }

    @PostMapping("/sites")
    public CrawlSite saveSite(@RequestBody CrawlSite site, Authentication authentication) {
        site.setId(null);
        return crawlService.saveSite(site, userId(authentication));
    }

    @PutMapping("/sites/{id}")
    public CrawlSite updateSite(
            @PathVariable Long id, @RequestBody CrawlSite site, Authentication authentication) {
        site.setId(id);
        return crawlService.saveSite(site, userId(authentication));
    }

    @GetMapping("/sites/{siteId}/rules")
    public List<CrawlRule> listRules(
            @PathVariable Long siteId, Authentication authentication) {
        return crawlService.listRules(siteId, userId(authentication));
    }

    @PostMapping("/rules")
    public CrawlRule saveRule(@RequestBody CrawlRule rule, Authentication authentication) {
        rule.setId(null);
        return crawlService.saveRule(rule, userId(authentication));
    }

    @PutMapping("/rules/{id}")
    public CrawlRule updateRule(
            @PathVariable Long id, @RequestBody CrawlRule rule, Authentication authentication) {
        rule.setId(id);
        return crawlService.saveRule(rule, userId(authentication));
    }

    @PostMapping("/rules/preview")
    public CrawlService.PreviewResult preview(
            @RequestBody CrawlRule rule, Authentication authentication) throws Exception {
        return crawlService.preview(rule, userId(authentication));
    }

    @GetMapping("/jobs")
    public List<CrawlJob> listJobs(Authentication authentication) {
        return crawlService.listJobs(userId(authentication));
    }

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> createJob(
            @RequestBody Map<String, Long> request, Authentication authentication) {
        CrawlJob job = crawlService.createJob(request.get("ruleId"), userId(authentication));
        crawlWorker.discover(job.getId());
        return ResponseEntity.accepted().body(Map.of("jobId", job.getId()));
    }

    @GetMapping("/jobs/{jobId}")
    public CrawlJob getJob(@PathVariable Long jobId, Authentication authentication) {
        return crawlService.getJob(jobId, userId(authentication));
    }

    @GetMapping("/jobs/{jobId}/pages")
    public CrawlService.PageResult listPages(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String query,
            Authentication authentication) {
        return crawlService.listPages(
                jobId, userId(authentication), query,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
    }

    @PatchMapping("/jobs/{jobId}/pages")
    public CrawlService.PageSelectionResult selectPages(
            @PathVariable Long jobId,
            @RequestBody PageSelectionRequest request,
            Authentication authentication) {
        return crawlService.setPagesIncluded(
                jobId, userId(authentication), request.ids(), request.included());
    }

    @PostMapping("/jobs/{jobId}/download")
    public ResponseEntity<Map<String, Object>> download(
            @PathVariable Long jobId, Authentication authentication) {
        crawlService.prepareDownload(jobId, userId(authentication));
        crawlWorker.download(jobId);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @PostMapping("/jobs/{jobId}/pause")
    public CrawlJob pause(@PathVariable Long jobId, Authentication authentication) {
        return crawlService.pause(jobId, userId(authentication));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public CrawlJob cancel(@PathVariable Long jobId, Authentication authentication) {
        return crawlService.cancel(jobId, userId(authentication));
    }

    @PostMapping("/jobs/{jobId}/resume")
    public ResponseEntity<Map<String, Object>> resume(
            @PathVariable Long jobId, Authentication authentication) {
        CrawlJob job = crawlService.prepareResumeOrRetry(jobId, userId(authentication));
        if (job.getPhase() == CrawlJob.Phase.DISCOVERY) crawlWorker.discover(jobId);
        else crawlWorker.download(jobId);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/jobs/{jobId}/assets")
    public Page<CrawlAsset> listAssets(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size,
            @RequestParam(required = false) CrawlAsset.Status status,
            @RequestParam(defaultValue = "false") boolean exactDuplicates,
            @RequestParam(defaultValue = "false") boolean similarOnly,
            Authentication authentication) {
        return crawlService.listAssets(
                jobId, userId(authentication), status, exactDuplicates, similarOnly,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
    }

    @PostMapping("/jobs/{jobId}/similarity")
    public CrawlSimilarityService.SimilarityResult analyzeSimilarity(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "8") int threshold,
            Authentication authentication) {
        return similarityService.analyze(jobId, userId(authentication), threshold);
    }

    @PostMapping("/jobs/{jobId}/assets/delete")
    public Map<String, Integer> deleteAssets(
            @PathVariable Long jobId,
            @RequestBody List<Long> ids,
            Authentication authentication) {
        return Map.of("success", crawlService.setAssetDeleted(
                ids, jobId, userId(authentication), true));
    }

    @PostMapping("/jobs/{jobId}/assets/restore")
    public Map<String, Integer> restoreAssets(
            @PathVariable Long jobId,
            @RequestBody List<Long> ids,
            Authentication authentication) {
        return Map.of("success", crawlService.setAssetDeleted(
                ids, jobId, userId(authentication), false));
    }

    @PostMapping("/jobs/{jobId}/import")
    public CrawlImportService.ImportResult importAssets(
            @PathVariable Long jobId,
            @RequestBody ImportRequest request,
            Authentication authentication) {
        CrawlImportService.ImportResult result = importService.importAssets(
                jobId, request.ids(), userId(authentication), request.albumId(), request.tagIds(),
                request.idempotencyKey());
        if (result.status() == com.aiphoto.entity.CrawlImportBatch.Status.QUEUED
                || result.status() == com.aiphoto.entity.CrawlImportBatch.Status.RUNNING) {
            importWorker.process(result.batchId());
        }
        return result;
    }

    @GetMapping("/imports/{batchId}")
    public CrawlImportService.BatchView getImportBatch(
            @PathVariable Long batchId, Authentication authentication) {
        return importService.getBatch(batchId, userId(authentication));
    }

    @GetMapping("/imports/{batchId}/items")
    public Page<CrawlImportItem> getImportBatchItems(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) CrawlImportItem.Status status,
            Authentication authentication) {
        return importService.getBatchItems(
                batchId, userId(authentication), status,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
    }

    @PostMapping("/jobs/{jobId}/import-preview")
    public CrawlImportService.ImportPreview previewImport(
            @PathVariable Long jobId,
            @RequestBody ImportRequest request,
            Authentication authentication) {
        return importService.preview(jobId, request.ids(), userId(authentication));
    }

    @PatchMapping("/jobs/{jobId}/assets")
    public Map<String, Integer> editAssets(
            @PathVariable Long jobId,
            @RequestBody EditAssetsRequest request,
            Authentication authentication) {
        return Map.of("success", crawlService.batchEditAssets(
                request.ids(), jobId, userId(authentication), request.note()));
    }

    @DeleteMapping("/jobs/{jobId}")
    public CrawlService.PurgeResult purgeJob(
            @PathVariable Long jobId, Authentication authentication) {
        return crawlService.purgeJob(jobId, userId(authentication));
    }

    @GetMapping("/assets/{assetId}/content")
    public ResponseEntity<byte[]> assetContent(
            @PathVariable Long assetId, Authentication authentication) throws Exception {
        CrawlAsset asset = crawlService.getAsset(assetId, userId(authentication));
        if (asset.getLocalPath() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .body(stagingStorage.read(asset.getLocalPath()));
    }

    @GetMapping("/assets/{assetId}/thumbnail")
    public ResponseEntity<byte[]> assetThumbnail(
            @PathVariable Long assetId, Authentication authentication) throws Exception {
        CrawlAsset asset = crawlService.getAsset(assetId, userId(authentication));
        String path = asset.getThumbnailPath() == null ? asset.getLocalPath() : asset.getThumbnailPath();
        if (path == null) return ResponseEntity.notFound().build();
        MediaType type = asset.getThumbnailPath() == null
                ? MediaType.parseMediaType(asset.getContentType()) : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(stagingStorage.read(path));
    }

    private Long userId(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return user.getId();
    }

    public record ImportRequest(
            List<Long> ids, Long albumId, List<Long> tagIds, String idempotencyKey) {}
    public record EditAssetsRequest(List<Long> ids, String note) {}
    public record PageSelectionRequest(List<Long> ids, boolean included) {}
}
