package com.jobsapply.platform.employer.web;

import com.jobsapply.platform.employer.BindingSpec;
import com.jobsapply.platform.employer.EmployerSpec;
import com.jobsapply.platform.employer.PreferenceMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** Request bodies of the employer API. Field constraints are enforced by the service ({@link EmployerSpec}). */
final class EmployerRequests {

    private EmployerRequests() {}

    record BindingBody(String sourceKey, String externalRef, Boolean enabled, Integer priority) {
        BindingSpec toSpec() {
            return new BindingSpec(sourceKey, externalRef, enabled == null || enabled, priority == null ? 50 : priority);
        }
    }

    record CreateEmployer(
            String key,
            String displayName,
            String website,
            String careersUrl,
            String industry,
            List<String> tags,
            String headquarters,
            Integer discoveryPriority,
            Boolean enabled,
            Map<String, Object> metadata,
            List<@Valid BindingBody> bindings) {

        EmployerSpec toSpec() {
            return new EmployerSpec(key, displayName, website, careersUrl, industry, tags, headquarters,
                    discoveryPriority == null ? 50 : discoveryPriority, enabled == null || enabled, metadata == null ? Map.of() : metadata);
        }

        List<BindingSpec> bindingSpecs() {
            return bindings == null ? List.of() : bindings.stream().map(BindingBody::toSpec).toList();
        }
    }

    /** Full replacement of the details. {@code version} must be the version last read (optimistic locking). */
    record UpdateEmployer(
            String displayName,
            String website,
            String careersUrl,
            String industry,
            List<String> tags,
            String headquarters,
            Integer discoveryPriority,
            Boolean enabled,
            Map<String, Object> metadata,
            @NotNull Long version) {

        EmployerSpec toSpec(String key) {
            return new EmployerSpec(key, displayName, website, careersUrl, industry, tags, headquarters,
                    discoveryPriority, enabled, metadata);
        }
    }

    record PutBinding(Boolean enabled, Integer priority) {}

    record PutPreference(@NotNull PreferenceMode mode, Integer priority, String note) {}
}
