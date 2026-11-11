package com.aiphoto.service;

import com.aiphoto.entity.Photo;
import com.aiphoto.repository.AlbumRepository;
import com.aiphoto.repository.CategoryRepository;
import com.aiphoto.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DedupService {

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Find exact duplicates by MD5 hash.
     */
    public record ExactGroup(String hash, List<Photo> photos, long total) {}
    public record ExactPage(List<ExactGroup> groups, boolean hasMore) {}

    public ExactPage exactPage(int page) {
        var hashes = photoRepository.duplicateHashes(org.springframework.data.domain.PageRequest.of(Math.max(0, page), 20));
        var groups = new ArrayList<ExactGroup>();
        for (String hash : hashes.subList(0, Math.min(20, hashes.size()))) {
            var members = photoRepository.duplicateMembers(hash, org.springframework.data.domain.PageRequest.of(0, 100));
            groups.add(new ExactGroup(hash, members.getContent(), members.getTotalElements()));
        }
        return new ExactPage(groups, hashes.size() == 20);
    }

    public List<List<Photo>> findExactDuplicates() {
        return exactPage(0).groups().stream().map(ExactGroup::photos).toList();
    }

    /**
     * Find similar photos by pHash (perceptual hash).
     * Photos with hamming distance <= 8 are considered similar.
     */
    public List<List<Photo>> findSimilarPhotos() {
        if (photoRepository.countWithPhash() > 5000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "相似分析暂支持最多 5000 张有感知哈希的照片；大图库候选索引尚未启用");
        }
        List<Photo> photosWithPhash = photoRepository.findPhashCandidates(org.springframework.data.domain.PageRequest.of(0, 5001));
        if (photosWithPhash.size() > 5000) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "相似分析最多支持 5000 张照片");
        var hashService = new PerceptualHashService();
        List<List<Photo>> groups = new ArrayList<>();
        boolean[] assigned = new boolean[photosWithPhash.size()];

        for (int i = 0; i < photosWithPhash.size(); i++) {
            if (assigned[i]) continue;

            List<Photo> group = new ArrayList<>();
            group.add(photosWithPhash.get(i));
            assigned[i] = true;

            for (int j = i + 1; j < photosWithPhash.size(); j++) {
                if (assigned[j]) continue;
                int distance = hashService.hammingDistance(
                        photosWithPhash.get(i).getFileHashPhash(),
                        photosWithPhash.get(j).getFileHashPhash()
                );
                if (distance <= 8) {
                    group.add(photosWithPhash.get(j));
                    assigned[j] = true;
                }
            }

            if (group.size() > 1) {
                groups.add(group);
            }
        }

        return groups;
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        albumRepository.clearCoverPhotoRefs(photoId);
        categoryRepository.clearCoverPhotoRefs(photoId);
        photo.setDeletedAt(java.time.LocalDateTime.now());
        photo.setFavorite(false);
        photo.setInTimeline(false);
        photoRepository.save(photo);
    }
}
