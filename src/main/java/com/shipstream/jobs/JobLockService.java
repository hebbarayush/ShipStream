package com.shipstream.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lease-based distributed lock on {@code job_locks}, so a scheduled job runs on exactly one instance
 * at a time even with several instances running the same {@code @Scheduled} method.
 */
@Service
public class JobLockService {

    private final JobLockRepository repo;
    private final String ownerId = "jvm-" + UUID.randomUUID();

    JobLockService(JobLockRepository repo) {
        this.repo = repo;
    }

    /** Acquires a fresh lease, either by taking over an expired row or inserting a new one. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String jobName, Duration leaseDuration) {
        Instant now = Instant.now();
        Instant until = now.plus(leaseDuration);
        if (repo.tryExtendExpiredLock(jobName, now, until, ownerId) == 1) {
            return true;
        }
        try {
            repo.save(new JobLock(jobName, until, ownerId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false; // another instance won the race to insert the row first
        }
    }

    /** Best-effort early release so the next scheduled run elsewhere doesn't wait out the full lease. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String jobName) {
        repo.releaseIfOwner(jobName, ownerId, Instant.now());
    }
}
