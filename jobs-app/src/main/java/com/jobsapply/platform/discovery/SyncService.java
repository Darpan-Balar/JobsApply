package com.jobsapply.platform.discovery;

import com.jobsapply.platform.discovery.internal.SyncEngine;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.SyncTarget;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Public API of discovery. Bindings are synced sequentially; requests to one provider host are additionally paced
 * by the adapters' rate limiter. Each binding's run is independent: one failing board never affects another.
 */
@Service
@RequiredArgsConstructor
public class SyncService {

    private final EmployerService employers;
    private final SyncEngine engine;

    /**
     * Syncs every active binding, optionally narrowed to one source and/or one employer.
     *
     * @param sourceKey {@code null} for all sources
     * @param employerKey {@code null} for all employers
     */
    public List<SyncReport> sync(String sourceKey, String employerKey, SyncTrigger trigger, boolean dryRun) {
        return employers.syncTargets(sourceKey, employerKey).stream()
                .map(target -> engine.sync(target, trigger, dryRun))
                .toList();
    }

    public SyncReport sync(SyncTarget target, SyncTrigger trigger, boolean dryRun) {
        return engine.sync(target, trigger, dryRun);
    }
}
