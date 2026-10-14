package com.jobsapply.platform.plugin;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jobs.plugins.*}.
 *
 * @param disabled plugin ids to exclude even though they are on the classpath. Registration alone never activates a
 *     plugin: a job source must name an adapter explicitly in its configuration (ADR-0003, Phase 2 amendment).
 */
@ConfigurationProperties("jobs.plugins")
public record PluginProperties(Set<String> disabled) {

    public PluginProperties {
        disabled = disabled == null ? Set.of() : Set.copyOf(disabled);
    }
}
