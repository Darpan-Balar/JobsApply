package com.jobsapply.spi.source;

import com.jobsapply.spi.common.HealthReport;
import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.ValidationReport;

/**
 * Discovers jobs from one kind of source (e.g. the Greenhouse Job Board API) for any number of employers.
 *
 * <p>Implementations must: use only documented interfaces; respect rate limits; bound retries; return expected failures
 * as {@link PluginResult.Failed}; and return {@link PluginResult.Unsupported} for capabilities they do not declare.
 */
public interface JobSourceAdapter extends Plugin {

    ValidationReport validateConfiguration(SourceBinding binding);

    PluginResult<DiscoveryPage> discoverJobs(DiscoveryRequest request);

    PluginResult<SourceJob> fetchJobDetails(SourceJobRef ref);

    PluginResult<AvailabilityObservation> checkAvailability(SourceJobRef ref);

    HealthReport healthCheck(SourceBinding binding);
}
