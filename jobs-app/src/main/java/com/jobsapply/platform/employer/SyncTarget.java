package com.jobsapply.platform.employer;

import java.util.Map;
import java.util.UUID;

/**
 * Everything discovery needs to sync one employer binding.
 *
 * @param settings the source's settings overlaid with the binding's own settings (binding wins)
 */
public record SyncTarget(
        UUID employerSourceId,
        UUID jobSourceId,
        UUID employerId,
        String employerKey,
        String sourceKey,
        String adapterId,
        String externalRef,
        Map<String, Object> settings) {

    public SyncTarget {
        settings = Map.copyOf(settings);
    }
}
