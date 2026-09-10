package com.aiphoto.service;

import com.aiphoto.entity.CrawlAsset;
import com.aiphoto.repository.CrawlAssetRepository;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrawlSimilarityService {

    private static final int MAX_ANALYSIS_ASSETS = 5000;
    private final CrawlService crawlService;
    private final CrawlAssetRepository assetRepository;
    private final PerceptualHashService hashService;
    private final CrawlStagingStorageService stagingStorage;

    @Transactional
    public SimilarityResult analyze(Long jobId, Long ownerId, Integer requestedThreshold) {
        crawlService.getJob(jobId, ownerId);
        int threshold = requestedThreshold == null ? 8 : Math.max(0, Math.min(16, requestedThreshold));
        assetRepository.clearSimilarityGroups(jobId);
        List<CrawlAsset> downloaded = assetRepository.findByJobIdAndStatus(
                jobId, CrawlAsset.Status.DOWNLOADED);
        if (downloaded.size() > MAX_ANALYSIS_ASSETS) {
            throw new IllegalStateException(
                    "单次最多分析 " + MAX_ANALYSIS_ASSETS + " 张可入库图片，请先筛选或删除部分图片");
        }
        for (CrawlAsset asset : downloaded) ensureHash(asset);
        List<CrawlAsset> assets = downloaded.stream()
                .filter(asset -> asset.getFileHashPhash() != null).toList();
        int[] parents = new int[assets.size()];
        for (int index = 0; index < parents.length; index++) parents[index] = index;
        for (int first = 0; first < assets.size(); first++) {
            for (int second = first + 1; second < assets.size(); second++) {
                if (hashService.hammingDistance(
                        assets.get(first).getFileHashPhash(),
                        assets.get(second).getFileHashPhash()) <= threshold) {
                    union(parents, first, second);
                }
            }
        }
        Map<Integer, List<CrawlAsset>> byRoot = new HashMap<>();
        for (int index = 0; index < assets.size(); index++) {
            byRoot.computeIfAbsent(find(parents, index), ignored -> new ArrayList<>())
                    .add(assets.get(index));
        }
        int groups = 0;
        int matched = 0;
        for (List<CrawlAsset> group : byRoot.values()) {
            if (group.size() < 2) continue;
            groups++;
            matched += group.size();
            Long groupId = group.stream().map(CrawlAsset::getId).min(Long::compareTo).orElseThrow();
            for (CrawlAsset asset : group) {
                asset.setSimilarityGroupId(groupId);
                asset.setSimilarityCount(group.size());
            }
        }
        assetRepository.saveAll(assets);
        return new SimilarityResult(assets.size(), groups, matched, threshold);
    }

    private void ensureHash(CrawlAsset asset) {
        if (asset.getFileHashPhash() != null || asset.getLocalPath() == null) return;
        try {
            var image = ImageIO.read(new ByteArrayInputStream(stagingStorage.read(asset.getLocalPath())));
            if (image != null) asset.setFileHashPhash(hashService.differenceHash(image));
        } catch (Exception ignored) {
            // An unreadable staging item remains outside similarity analysis.
        }
    }

    private int find(int[] parents, int value) {
        while (parents[value] != value) {
            parents[value] = parents[parents[value]];
            value = parents[value];
        }
        return value;
    }

    private void union(int[] parents, int first, int second) {
        int firstRoot = find(parents, first);
        int secondRoot = find(parents, second);
        if (firstRoot != secondRoot) parents[secondRoot] = firstRoot;
    }

    public record SimilarityResult(int analyzed, int groups, int matched, int threshold) {}
}
