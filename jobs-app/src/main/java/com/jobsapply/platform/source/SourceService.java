package com.jobsapply.platform.source;

import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.plugin.PluginSettingsBinder;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.UpsertOutcome;
import com.jobsapply.platform.source.internal.JobSource;
import com.jobsapply.platform.source.internal.JobSourceRepository;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.source.JobSourceAdapter;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Public API of the source module. */
@Service
@Validated
@RequiredArgsConstructor
public class SourceService {

    private final JobSourceRepository sources;
    private final PluginRegistry<JobSourceAdapter> adapters;
    private final PluginSettingsBinder settingsBinder;
    private final JsonCodec json;

    /**
     * Checks a specification against the installed adapters: an enabled source needs an installed adapter and valid
     * settings; a disabled source may name an adapter that is not installed (reported as a notice).
     */
    public SourceCheck check(JobSourceSpec spec) {
        List<SourceCheck.Issue> issues = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        Optional<JobSourceAdapter> adapter = adapters.find(spec.adapterId());
        if (adapter.isEmpty()) {
            if (spec.enabled()) {
                issues.add(new SourceCheck.Issue("adapter", "adapter '" + spec.adapterId()
                        + "' is not installed; installed: " + installedIds() + ". Disable the source or install the adapter"));
            } else {
                notices.add("source '" + spec.key() + "' stays disabled: adapter '" + spec.adapterId() + "' is not installed");
            }
            return new SourceCheck(issues, notices);
        }
        PluginDescriptor descriptor = adapter.get().descriptor();
        settingsBinder.bind(descriptor, spec.settings()).report().issues().forEach(issue ->
                issues.add(new SourceCheck.Issue(issue.path().isEmpty() ? "settings" : "settings." + issue.path(), issue.message())));
        return new SourceCheck(issues, notices);
    }

    @Transactional
    public UpsertOutcome upsert(@Valid JobSourceSpec spec) {
        SourceCheck check = check(spec);
        if (!check.isValid()) {
            throw new DomainRuleViolationException("Invalid job source '" + spec.key() + "': " + check.issues().stream()
                    .map(i -> i.path() + ": " + i.message()).collect(Collectors.joining("; ")));
        }
        String settingsJson = json.write(spec.settings());
        Optional<JobSource> existing = sources.findByKey(spec.key());
        if (existing.isEmpty()) {
            sources.save(JobSource.builder()
                    .key(spec.key())
                    .adapterId(spec.adapterId())
                    .displayName(spec.displayName())
                    .enabled(spec.enabled())
                    .settingsJson(settingsJson)
                    .build());
            return UpsertOutcome.CREATED;
        }
        JobSource source = existing.get();
        if (json.canonicalize(source.getSettingsJson()).equals(settingsJson)) {
            settingsJson = source.getSettingsJson(); // same content; keep the stored text so no change is detected
        }
        return source.update(spec.adapterId(), spec.displayName(), spec.enabled(), settingsJson)
                ? UpsertOutcome.UPDATED
                : UpsertOutcome.UNCHANGED;
    }

    @Transactional(readOnly = true)
    public List<JobSourceView> list() {
        return sources.findAllByOrderByKey().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<JobSourceView> find(String key) {
        return sources.findByKey(key).map(this::toView);
    }

    @Transactional(readOnly = true)
    public JobSourceView require(String key) {
        return find(key).orElseThrow(() -> new NotFoundException("Job source", key));
    }

    /** Source key → id for all configured sources. */
    @Transactional(readOnly = true)
    public Map<String, UUID> idsByKey() {
        return sources.findAll().stream().collect(Collectors.toUnmodifiableMap(JobSource::getKey, JobSource::getId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, JobSourceView> viewsById() {
        return sources.findAll().stream().collect(Collectors.toUnmodifiableMap(JobSource::getId, this::toView));
    }

    @Transactional(readOnly = true)
    public Set<UUID> idsByAdapter(String adapterId) {
        return sources.findByAdapterId(adapterId).stream().map(JobSource::getId).collect(Collectors.toUnmodifiableSet());
    }

    public List<PluginDescriptor> installedAdapters() {
        return adapters.descriptors();
    }

    private String installedIds() {
        List<String> ids = adapters.descriptors().stream().map(PluginDescriptor::id).toList();
        return ids.isEmpty() ? "none" : String.join(", ", ids);
    }

    @SuppressWarnings("unchecked")
    private JobSourceView toView(JobSource source) {
        return new JobSourceView(source.getId(), source.getKey(), source.getAdapterId(), source.getDisplayName(),
                source.isEnabled(), adapters.isRegistered(source.getAdapterId()),
                json.read(source.getSettingsJson(), Map.class), source.getVersion() == null ? 0 : source.getVersion());
    }
}
