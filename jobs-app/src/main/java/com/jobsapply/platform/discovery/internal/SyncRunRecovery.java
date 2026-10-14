package com.jobsapply.platform.discovery.internal;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A crash leaves RUNNING runs behind, which would block their bindings forever (single-flight index). With a single
 * application instance nothing can be running at startup, so they are marked ABORTED. Multi-instance deployments
 * need a lease/heartbeat instead (Phase 9).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
class SyncRunRecovery implements ApplicationRunner {

    private final SyncRunRepository runs;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int aborted = recover(clock.instant());
        if (aborted > 0) {
            log.warn("Marked {} interrupted sync run(s) as ABORTED", aborted);
        }
    }

    int recover(Instant now) {
        return runs.abortRunning(now);
    }
}
