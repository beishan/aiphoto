package com.memoryvault.service;

import com.memoryvault.entity.Photo;
import com.memoryvault.repository.AlbumRepository;
import com.memoryvault.repository.CategoryRepository;
import com.memoryvault.repository.PhotoRepository;
import com.memoryvault.storage.LocalStorageService;
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
    private final LocalStorageService storageService;
    private final AlbumRepository albumRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Find exact duplicates by MD5 hash.
     */
    public List<List<Photo>> findExactDuplicates() {
        List<Photo> allPhotos = photoRepository.findAll();
        Map<String, List<Photo>> hashGroups = new HashMap<>();

        for (Photo photo : allPhotos) {
            if (photo.getFileHashMd5() != null) {
                hashGroups.computeIfAbsent(photo.getFileHashMd5(), k -> new ArrayList<>()).add(photo);
            }
        }

        return hashGroups.values().stream()
                .filter(group -> group.size() > 1)
                .toList();
    }

    /**
     * Find similar photos by pHash (perceptual hash).
     * Photos with hamming distance <= 8 are considered similar.
     */
    public List<List<Photo>> findSimilarPhotos() {
        List<Photo> photosWithPhash = photoRepository.findAllWithPhash();
        List<List<Photo>> groups = new ArrayList<>();
        boolean[] assigned = new boolean[photosWithPhash.size()];

        for (int i = 0; i < photosWithPhash.size(); i++) {
            if (assigned[i]) continue;

            List<Photo> group = new ArrayList<>();
            group.add(photosWithPhash.get(i));
            assigned[i] = true;

            for (int j = i + 1; j < photosWithPhash.size(); j++) {
                if (assigned[j]) continue;
                int distance = hammingDistance(
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

    private int hammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != hash2.length()) {
            return Integer.MAX_VALUE;
        }
        int distance = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new RuntimeException("Photo not found"));
        // Clear cover_photo_id references from albums and categories
        albumRepository.clearCoverPhotoRefs(photoId);
        categoryRepository.clearCoverPhotoRefs(photoId);
        try {
            storageService.deleteObject(photo.getFilePath());
            String thumbExt = photo.getOriginalFilename() != null
                    && photo.getOriginalFilename().toLowerCase().endsWith(".webp") ? "webp" : "jpg";
            storageService.deleteObject(photo.getFileHashMd5() + "/thumb." + thumbExt);
        } catch (Exception e) {
            log.warn("Failed to delete storage objects for photo {}: {}", photoId, e.getMessage());
        }
        photoRepository.deleteById(photoId);
    }
}
