package com.jobsapply.platform.support;

import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.FailureKind;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test adapter whose responses are scripted per board (external ref): a sequence of pages (cursor = page index) and
 * per-job availability answers. Lets discovery tests reproduce every source behaviour deterministically.
 */
public class ScriptedJobSourceAdapter implements JobSourceAdapter {

    public static final String ID = "scripted-board";

    public record Settings() implements PluginSettings {}

    private final Map<String, List<PluginResult<DiscoveryPage>>> pages = new ConcurrentHashMap<>();
    private final Map<String, PluginResult<AvailabilityObservation>> availability = new ConcurrentHashMap<>();

    /** Replaces the script for a board. Pages after the first need a cursor chain: use {@link #page}. */
    public void script(String board, List<PluginResult<DiscoveryPage>> boardPages) {
        pages.put(board, new ArrayList<>(boardPages));
    }

    public void availability(String board, String externalId, PluginResult<AvailabilityObservation> answer) {
        availability.put(board + "/" + externalId, answer);
    }

    /** A page; {@code hasNext} links to the following scripted page. */
    public static PluginResult<DiscoveryPage> page(int index, boolean hasNext, boolean complete, List<SourceJob> jobs,
            List<DiscoveryPage.ItemProblem> problems) {
        return PluginResult.ok(new DiscoveryPage(jobs, hasNext ? String.valueOf(index + 1) : null, complete, problems));
    }

    public static PluginResult<DiscoveryPage> completePage(SourceJob... jobs) {
        return page(0, false, true, List.of(jobs), List.of());
    }

    public static <T> PluginResult<T> failure(FailureKind kind) {
        return PluginResult.failed(kind, "scripted " + kind);
    }

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.builder()
                .id(ID)
                .version("test")
                .kind(PluginKind.JOB_SOURCE)
                .capabilities(EnumSet.of(Capability.LIST_JOBS, Capability.AVAILABILITY_CHECK))
                .settingsType(Settings.class)
                .settingsSchemaVersion(1)
                .build();
    }

    @Override
    public ValidationReport validateConfiguration(SourceBinding binding) {
        return ValidationReport.valid();
    }

    @Override
    public PluginResult<DiscoveryPage> discoverJobs(DiscoveryRequest request) {
        List<PluginResult<DiscoveryPage>> script = pages.get(request.binding().externalRef());
        int index = request.cursor() == null ? 0 : Integer.parseInt(request.cursor());
        if (script == null || index >= script.size()) {
            return PluginResult.failed(FailureKind.NOT_FOUND, "no script");
        }
        return script.get(index);
    }

    @Override
    public PluginResult<SourceJob> fetchJobDetails(SourceJobRef ref) {
        return PluginResult.unsupported(Capability.JOB_DETAILS);
    }

    @Override
    public PluginResult<AvailabilityObservation> checkAvailability(SourceJobRef ref) {
        return availability.getOrDefault(ref.binding().externalRef() + "/" + ref.externalId(),
                PluginResult.failed(FailureKind.TRANSIENT, "no availability script"));
    }

    @Override
    public HealthReport healthCheck(SourceBinding binding) {
        return new HealthReport(HealthReport.Status.UNKNOWN, "scripted", Instant.EPOCH);
    }
}
