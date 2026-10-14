package com.jobsapply.platform.source.web;

import com.jobsapply.platform.shared.model.UpsertOutcome;
import com.jobsapply.platform.source.JobSourceSpec;
import com.jobsapply.platform.source.JobSourceView;
import com.jobsapply.platform.source.SourceService;
import com.jobsapply.spi.common.Capability;
import com.jobsapply.spi.common.PluginDescriptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Job sources", description = "Configured job-source instances and installed adapter plugins")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class SourceController {

    private final SourceService sources;

    record PutSource(String adapterId, String displayName, Boolean enabled, Map<String, Object> settings) {}

    record UpsertResult(String key, UpsertOutcome outcome, JobSourceView source) {}

    /** Descriptor without implementation types. */
    record AdapterView(String id, String version, Set<Capability> capabilities, String settingsType,
            int settingsSchemaVersion, URI documentation, boolean authenticationRequired, String termsNotes) {}

    @GetMapping("/sources")
    List<JobSourceView> list() {
        return sources.list();
    }

    @GetMapping("/sources/{key}")
    JobSourceView get(@PathVariable String key) {
        return sources.require(key);
    }

    @Operation(summary = "Create or replace a job source",
            description = "An enabled source needs an installed adapter and settings valid for it. Settings may hold secret references (env:NAME) only.")
    @PutMapping("/sources/{key}")
    UpsertResult put(@PathVariable String key, @RequestBody PutSource body) {
        UpsertOutcome outcome = sources.upsert(new JobSourceSpec(key, body.adapterId(), body.displayName(),
                body.enabled() == null ? Boolean.FALSE : body.enabled(), body.settings()));
        return new UpsertResult(key, outcome, sources.require(key));
    }

    @GetMapping("/plugins/job-source-adapters")
    List<AdapterView> adapters() {
        return sources.installedAdapters().stream().map(SourceController::toView).toList();
    }

    private static AdapterView toView(PluginDescriptor d) {
        var compliance = d.compliance();
        return new AdapterView(d.id(), d.version(), d.capabilities(), d.settingsType().getSimpleName(), d.settingsSchemaVersion(),
                compliance == null ? null : compliance.documentation(),
                compliance != null && compliance.authenticationRequired(),
                compliance == null ? null : compliance.termsNotes());
    }
}
