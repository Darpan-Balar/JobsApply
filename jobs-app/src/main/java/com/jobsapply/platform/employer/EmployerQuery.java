package com.jobsapply.platform.employer;

import lombok.Builder;

/**
 * Search criteria; {@code null} means "any".
 *
 * @param sourceKey only employers with an enabled binding on this job source
 * @param adapterId only employers with an enabled binding on a source using this adapter (source type)
 * @param text case-insensitive substring of display name or key
 */
@Builder
public record EmployerQuery(
        String tag,
        String industry,
        Integer minPriority,
        String sourceKey,
        String adapterId,
        Boolean enabled,
        boolean includeArchived,
        String text) {

    public static EmployerQuery all() {
        return EmployerQuery.builder().build();
    }
}
