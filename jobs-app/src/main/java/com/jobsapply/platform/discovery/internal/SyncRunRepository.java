package com.jobsapply.platform.discovery.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    /** Crash recovery (single instance): nothing can still be running when the application starts. */
    @Modifying
    @Query("""
            update SyncRun r set r.status = com.jobsapply.platform.discovery.SyncRunStatus.ABORTED, r.finishedAt = :now,
                   r.errorSummary = 'aborted: the application stopped while this run was in progress'
            where r.status = com.jobsapply.platform.discovery.SyncRunStatus.RUNNING
            """)
    int abortRunning(Instant now);

    List<SyncRun> findByOrderByStartedAtDesc(Pageable pageable);

    List<SyncRun> findByJobSourceIdOrderByStartedAtDesc(UUID jobSourceId, Pageable pageable);

    List<SyncRun> findByEmployerSourceIdInOrderByStartedAtDesc(Collection<UUID> employerSourceIds, Pageable pageable);
}
