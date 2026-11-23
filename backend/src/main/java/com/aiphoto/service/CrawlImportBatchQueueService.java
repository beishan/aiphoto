package com.aiphoto.service;

import com.aiphoto.repository.CrawlImportBatchRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlImportBatchQueueService {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(10);
    private final CrawlImportBatchRepository batchRepository;
    private final CrawlDiskGuard diskGuard;
    private final String instanceId = UUID.randomUUID().toString();

    public String newWorkerId() {
        return instanceId + ":" + UUID.randomUUID();
    }

    public boolean claim(Long batchId, String workerId) {
        if (diskGuard.blocked()) return false;
        LocalDateTime now = LocalDateTime.now();
        return batchRepository.claim(batchId, workerId, now, now.plus(LEASE_DURATION)) == 1;
    }

    public boolean renew(Long batchId, String workerId) {
        if (diskGuard.blocked()) {
            batchRepository.yieldForDiskSpace(batchId, workerId);
            return false;
        }
        return batchRepository.renewLease(
                batchId, workerId, LocalDateTime.now().plus(LEASE_DURATION)) == 1;
    }
}
