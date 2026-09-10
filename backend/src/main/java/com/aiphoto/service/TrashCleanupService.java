package com.aiphoto.service;

import com.aiphoto.repository.PhotoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrashCleanupService {

    private final PhotoRepository photoRepository;
    private final PhotoService photoService;

    public TrashCleanupResult clearTrash() {
        List<Long> photoIds = photoRepository.findTrashIds();
        int success = 0;
        int fail = 0;

        for (Long photoId : photoIds) {
            try {
                photoService.permanentDeletePhoto(photoId);
                success++;
            } catch (Exception exception) {
                fail++;
                log.warn(
                        "Failed to permanently delete photo {} while clearing trash: {}",
                        photoId,
                        exception.getMessage());
            }
        }

        return new TrashCleanupResult(success, fail);
    }

    public record TrashCleanupResult(int success, int fail) {}
}
