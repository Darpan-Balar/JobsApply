package com.jobsapply.platform.operations.internal;

import com.jobsapply.platform.application.ApplicationEvents;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Counters for what the platform does (tags are codes only; never ids or personal data). Readable by administrators at
 * {@code /actuator/metrics}: {@code jobs.application.transitions}, {@code jobs.submission.outcomes}; sign-ins are
 * counted as {@code jobs.auth.signins}, synchronization as {@code jobs.sync.*}, scheduled jobs as {@code jobs.scheduled.runs}.
 */
@Component
@RequiredArgsConstructor
class DomainMetrics {

    private final MeterRegistry meters;

    @EventListener
    void on(ApplicationEvents.StatusChanged e) {
        meters.counter("jobs.application.transitions", "to", e.to().name(), "actor", e.actorType().name()).increment();
    }

    @EventListener
    void on(ApplicationEvents.AttemptFinished e) {
        meters.counter("jobs.submission.outcomes", "outcome", e.outcome().name(), "actor", e.actorType().name()).increment();
    }
}
