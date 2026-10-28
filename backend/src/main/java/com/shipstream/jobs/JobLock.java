package com.shipstream.jobs;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A leased, named lock: whoever holds a non-expired lease owns the job. Reused across all jobs. */
@Entity
@Table(name = "job_locks")
@Getter
@NoArgsConstructor
public class JobLock {

    @Id
    @Column(name = "job_name")
    private String jobName;

    @Column(name = "locked_until", nullable = false)
    private Instant lockedUntil;

    @Column(name = "locked_by", nullable = false)
    private String lockedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public JobLock(String jobName, Instant lockedUntil, String lockedBy) {
        this.jobName = jobName;
        this.lockedUntil = lockedUntil;
        this.lockedBy = lockedBy;
        this.updatedAt = Instant.now();
    }
}
