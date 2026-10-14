package com.jobsapply.platform.employer;

import java.time.Instant;

/** A binding with its synchronization health (updated by discovery from Phase 3). */
public record BindingView(
        String sourceKey,
        String adapterId,
        String externalRef,
        boolean enabled,
        int priority,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        int consecutiveFailures,
        String lastError) {}
