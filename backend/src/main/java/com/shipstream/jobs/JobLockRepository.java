package com.shipstream.jobs;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobLockRepository extends JpaRepository<JobLock, String> {

    /** Only succeeds (returns 1) if the existing lease has expired; that's the whole lock protocol. */
    @Modifying
    @Query("""
            UPDATE JobLock l SET l.lockedUntil = :until, l.lockedBy = :owner, l.updatedAt = :now
            WHERE l.jobName = :jobName AND l.lockedUntil < :now
            """)
    int tryExtendExpiredLock(@Param("jobName") String jobName, @Param("now") Instant now,
            @Param("until") Instant until, @Param("owner") String owner);

    @Modifying
    @Query("UPDATE JobLock l SET l.lockedUntil = :now WHERE l.jobName = :jobName AND l.lockedBy = :owner")
    int releaseIfOwner(@Param("jobName") String jobName, @Param("owner") String owner, @Param("now") Instant now);
}
