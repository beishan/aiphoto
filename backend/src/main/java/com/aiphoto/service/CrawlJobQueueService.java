package com.aiphoto.service;

import com.aiphoto.repository.CrawlJobRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlJobQueueService {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(10);
    private final CrawlJobRepository jobRepository;
    private final CrawlDiskGuard diskGuard;
    private final String instanceId = UUID.randomUUID().toString();

    public String newWorkerId() {
        return instanceId + ":" + UUID.randomUUID();
    }

    public boolean claim(Long jobId, String workerId) {
        if (diskGuard.blocked()) return false;
        LocalDateTime now = LocalDateTime.now();
        return jobRepository.claim(jobId, workerId, now, now.plus(LEASE_DURATION)) == 1;
    }

    public boolean renew(Long jobId, String workerId) {
        if (diskGuard.blocked()) {
            jobRepository.yieldForDiskSpace(jobId, workerId);
            return false;
        }
        return jobRepository.renewLease(
                jobId, workerId, LocalDateTime.now().plus(LEASE_DURATION)) == 1;
    }
}
