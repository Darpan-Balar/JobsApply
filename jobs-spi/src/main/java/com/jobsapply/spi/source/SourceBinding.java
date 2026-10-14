package com.jobsapply.spi.source;

import com.jobsapply.spi.common.Lists;
import java.util.Map;

/**
 * One employer's presence on one source, e.g. Greenhouse board token {@code acme}. A single adapter serves many
 * bindings.
 *
 * @param sourceKey configured job-source instance key
 * @param externalRef provider identifier for the employer (board token, site name, ...)
 * @param settings binding-level settings already merged with source-level settings; never contains secrets
 */
public record SourceBinding(String sourceKey, String externalRef, Map<String, Object> settings) {

    public SourceBinding {
        settings = Lists.immutable(settings);
    }
}
