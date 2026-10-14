package com.jobsapply.platform.employer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** @param version optimistic-lock version; send it back when updating */
public record EmployerView(
        UUID id,
        String key,
        String displayName,
        String normalizedName,
        String website,
        String careersUrl,
        String industry,
        List<String> tags,
        String headquarters,
        int discoveryPriority,
        boolean enabled,
        Instant archivedAt,
        Map<String, Object> metadata,
        List<BindingView> bindings,
        long version) {

    public EmployerView {
        tags = List.copyOf(tags);
        bindings = List.copyOf(bindings);
    }
}
