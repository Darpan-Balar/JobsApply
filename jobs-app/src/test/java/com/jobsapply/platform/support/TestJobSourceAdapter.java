package com.jobsapply.platform.support;

import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.HealthReport;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.common.ValidationReport;
import com.jobsapply.spi.source.AvailabilityObservation;
import com.jobsapply.spi.source.DiscoveryPage;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.JobSourceAdapter;
import com.jobsapply.spi.source.SourceBinding;
import com.jobsapply.spi.source.SourceJob;
import com.jobsapply.spi.source.SourceJobRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/** Test-only adapter: exercises registration and settings validation. It discovers nothing. */
public class TestJobSourceAdapter implements JobSourceAdapter {

    public static final String ID = "test-job-board";

    public record Settings(@NotNull Duration requestTimeout, @Min(0) @Max(10) Integer maxRetries) implements PluginSettings {}

    private final String id;

    public TestJobSourceAdapter() {
        this(ID);
    }

    public TestJobSourceAdapter(String id) {
        this.id = id;
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id(id)
                .version("0.0.1-test")
                .kind(PluginKind.JOB_SOURCE)
                .capabilities(EnumSet.of(Capability.LIST_JOBS))
                .settingsType(Settings.class)
                .settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "test only"))
                .build();
    }

    @Override
    public ValidationReport validateConfiguration(SourceBinding binding) {
        return ValidationReport.valid();
    }

    @Override
    public PluginResult<DiscoveryPage> discoverJobs(DiscoveryRequest request) {
        return PluginResult.unsupported(Capability.LIST_JOBS);
    }

    @Override
    public PluginResult<SourceJob> fetchJobDetails(SourceJobRef ref) {
        return PluginResult.unsupported(Capability.JOB_DETAILS);
    }

    @Override
    public PluginResult<AvailabilityObservation> checkAvailability(SourceJobRef ref) {
        return PluginResult.unsupported(Capability.AVAILABILITY_CHECK);
    }

    @Override
    public HealthReport healthCheck(SourceBinding binding) {
        return new HealthReport(HealthReport.Status.UNKNOWN, "test adapter", Instant.EPOCH);
    }
}
