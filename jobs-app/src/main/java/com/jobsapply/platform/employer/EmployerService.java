package com.jobsapply.platform.employer;

import com.jobsapply.platform.employer.internal.Employer;
import com.jobsapply.platform.employer.internal.EmployerRepository;
import com.jobsapply.platform.employer.internal.EmployerSearch;
import com.jobsapply.platform.employer.internal.EmployerSource;
import com.jobsapply.platform.employer.internal.EmployerSourceRepository;
import com.jobsapply.platform.shared.error.ConcurrencyConflictException;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.model.PageResult;
import com.jobsapply.platform.shared.model.UpsertOutcome;
import com.jobsapply.platform.source.JobSourceView;
import com.jobsapply.platform.source.SourceService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Public API of the employer registry. Writes are validated and keyed by the employer's business key. */
@Service
@Validated
@RequiredArgsConstructor
public class EmployerService {

    private final EmployerRepository employers;
    private final EmployerSourceRepository bindings;
    private final SourceService sources;
    private final EmployerSearch search;
    private final JsonCodec json;
    private final Clock clock;

    @Transactional
    public EmployerView create(@Valid EmployerSpec spec, List<@Valid BindingSpec> bindingSpecs) {
        if (employers.findByKey(spec.key()).isPresent()) {
            throw new DomainRuleViolationException("Employer already exists: " + spec.key());
        }
        Employer employer = employers.save(newEmployer(spec));
        Map<String, UUID> sourceIds = sources.idsByKey();
        for (BindingSpec binding : bindingSpecs) {
            putBinding(employer, binding, sourceIds);
        }
        return view(employer);
    }

    /** Full update of the details, guarded by the version the client last saw. Bindings are managed separately. */
    @Transactional
    public EmployerView update(String key, @Valid EmployerSpec spec, long expectedVersion) {
        if (!key.equals(spec.key())) {
            throw new DomainRuleViolationException("Employer key cannot be changed (" + key + " -> " + spec.key() + ")");
        }
        Employer employer = load(key);
        long current = employer.getVersion();
        if (current != expectedVersion) {
            throw new ConcurrencyConflictException("Employer", key, expectedVersion, current);
        }
        employer.update(spec, EmployerNames.normalize(spec.displayName()), metadataJson(spec, employer));
        employers.flush();
        return view(employer);
    }

    /**
     * Idempotent import of an employer and its declared bindings: missing bindings are created, changed ones updated,
     * and bindings no longer declared are disabled (never deleted — listings reference them). Archive state is kept.
     */
    @Transactional
    public UpsertOutcome upsert(@Valid EmployerSpec spec, List<@Valid BindingSpec> declared) {
        Map<String, UUID> sourceIds = sources.idsByKey();
        Optional<Employer> existing = employers.findByKey(spec.key());
        Employer employer;
        boolean changed;
        if (existing.isEmpty()) {
            employer = employers.save(newEmployer(spec));
            changed = true;
        } else {
            employer = existing.get();
            changed = employer.update(spec, EmployerNames.normalize(spec.displayName()), metadataJson(spec, employer));
        }

        Set<String> declaredRefs = new HashSet<>();
        for (BindingSpec binding : declared) {
            declaredRefs.add(sourceIdOf(binding.sourceKey(), sourceIds) + "/" + binding.externalRef());
            changed |= putBinding(employer, binding, sourceIds);
        }
        for (EmployerSource binding : bindings.findByEmployerId(employer.getId())) {
            if (!declaredRefs.contains(binding.getJobSourceId() + "/" + binding.getExternalRef())) {
                changed |= binding.configure(false, binding.getPriority());
            }
        }
        if (existing.isEmpty()) {
            return UpsertOutcome.CREATED;
        }
        return changed ? UpsertOutcome.UPDATED : UpsertOutcome.UNCHANGED;
    }

    /** Creates or reconfigures one binding. A provider identifier can belong to only one employer. */
    @Transactional
    public BindingView putBinding(String employerKey, @Valid BindingSpec binding) {
        Employer employer = load(employerKey);
        putBinding(employer, binding, sources.idsByKey());
        return bindingViews(List.of(employer.getId())).get(employer.getId()).stream()
                .filter(b -> b.sourceKey().equals(binding.sourceKey()) && b.externalRef().equals(binding.externalRef()))
                .findFirst()
                .orElseThrow();
    }

    /** Disables a binding (it is kept: listings discovered through it reference it). */
    @Transactional
    public void disableBinding(String employerKey, String sourceKey, String externalRef) {
        Employer employer = load(employerKey);
        UUID sourceId = sourceIdOf(sourceKey, sources.idsByKey());
        EmployerSource binding = bindings.findByJobSourceIdAndExternalRef(sourceId, externalRef)
                .filter(b -> b.getEmployerId().equals(employer.getId()))
                .orElseThrow(() -> new NotFoundException("Binding", employerKey + " on " + sourceKey + "/" + externalRef));
        binding.configure(false, binding.getPriority());
    }

    @Transactional
    public EmployerView archive(String key) {
        Employer employer = load(key);
        employer.archive(Instant.now(clock));
        employers.flush();
        return view(employer);
    }

    @Transactional
    public EmployerView unarchive(String key) {
        Employer employer = load(key);
        employer.unarchive();
        employers.flush();
        return view(employer);
    }

    @Transactional(readOnly = true)
    public EmployerView get(String key) {
        return view(load(key));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findId(String key) {
        return employers.findByKey(key).map(Employer::getId);
    }

    @Transactional(readOnly = true)
    public PageResult<EmployerView> search(EmployerQuery query, EmployerSort sort, int page, int size) {
        Page<Employer> result = search.find(query, PageRequest.of(page, size, sort.sort()));
        Map<UUID, List<BindingView>> bindingsByEmployer = bindingViews(result.getContent().stream().map(Employer::getId).toList());
        return PageResult.of(result, e -> view(e, bindingsByEmployer.getOrDefault(e.getId(), List.of())));
    }

    /** Every employer (optionally including archived ones), ordered by key, for export. */
    @Transactional(readOnly = true)
    public List<EmployerView> exportAll(boolean includeArchived) {
        List<Employer> all = search.findAll(EmployerQuery.builder().includeArchived(includeArchived).build(), EmployerSort.KEY.sort());
        Map<UUID, List<BindingView>> bindingsByEmployer = bindingViews(all.stream().map(Employer::getId).toList());
        return all.stream().map(e -> view(e, bindingsByEmployer.getOrDefault(e.getId(), List.of()))).toList();
    }

    /** {@code "sourceKey/externalRef" → employer key} for every binding, for import validation. */
    @Transactional(readOnly = true)
    public Map<String, String> bindingOwners() {
        Map<UUID, String> sourceKeys = sources.viewsById().values().stream()
                .collect(Collectors.toMap(JobSourceView::id, JobSourceView::key));
        Map<UUID, String> employerKeys = employers.findAll().stream().collect(Collectors.toMap(Employer::getId, Employer::getKey));
        Map<String, String> owners = new HashMap<>();
        for (EmployerSource binding : bindings.findAll()) {
            owners.put(sourceKeys.get(binding.getJobSourceId()) + "/" + binding.getExternalRef(), employerKeys.get(binding.getEmployerId()));
        }
        return owners;
    }

    /**
     * Active sync targets: enabled, non-archived employers × enabled bindings × enabled sources, optionally narrowed to
     * one source and/or one employer.
     */
    @Transactional(readOnly = true)
    public List<SyncTarget> syncTargets(String sourceKey, String employerKey) {
        Map<UUID, JobSourceView> sourceViews = sources.viewsById();
        Map<UUID, Employer> active = (employerKey == null ? employers.findAll() : employers.findByKey(employerKey).stream().toList())
                .stream().filter(e -> e.isEnabled() && !e.isArchived())
                .collect(Collectors.toMap(Employer::getId, e -> e));
        if (active.isEmpty()) {
            return List.of();
        }
        return bindings.findByEmployerIdIn(active.keySet()).stream()
                .filter(EmployerSource::isEnabled)
                .filter(b -> {
                    JobSourceView source = sourceViews.get(b.getJobSourceId());
                    return source.enabled() && (sourceKey == null || source.key().equals(sourceKey));
                })
                .sorted(Comparator.comparing((EmployerSource b) -> -active.get(b.getEmployerId()).getDiscoveryPriority())
                        .thenComparing(b -> -b.getPriority())
                        .thenComparing(EmployerSource::getExternalRef))
                .map(b -> toTarget(b, active.get(b.getEmployerId()), sourceViews.get(b.getJobSourceId())))
                .toList();
    }

    /** The target for one binding regardless of enabled flags (used for on-demand availability rechecks). */
    @Transactional(readOnly = true)
    public Optional<SyncTarget> syncTarget(UUID employerSourceId) {
        return bindings.findById(employerSourceId).map(b -> toTarget(b,
                employers.findById(b.getEmployerId()).orElseThrow(), sources.viewsById().get(b.getJobSourceId())));
    }

    /** Records the outcome of a sync on the binding (health shown in the registry). */
    @Transactional
    public void recordSyncOutcome(UUID employerSourceId, boolean success, Instant at, String error) {
        bindings.findById(employerSourceId).ifPresent(b -> b.recordSync(success, at, error));
    }

    @Transactional(readOnly = true)
    public Map<UUID, EmployerRef> refs(Collection<UUID> employerIds) {
        return employers.findAllById(employerIds).stream().collect(Collectors.toMap(Employer::getId,
                e -> new EmployerRef(e.getId(), e.getKey(), e.getDisplayName())));
    }

    @Transactional(readOnly = true)
    public Map<UUID, BindingRef> bindingRefs(Collection<UUID> employerSourceIds) {
        Map<UUID, JobSourceView> sourceViews = sources.viewsById();
        List<EmployerSource> found = bindings.findAllById(employerSourceIds);
        Map<UUID, String> employerKeys = employers.findAllById(found.stream().map(EmployerSource::getEmployerId).toList())
                .stream().collect(Collectors.toMap(Employer::getId, Employer::getKey));
        return found.stream().collect(Collectors.toMap(EmployerSource::getId, b -> new BindingRef(
                b.getId(), employerKeys.get(b.getEmployerId()), sourceViews.get(b.getJobSourceId()).key(), b.getExternalRef())));
    }

    @SuppressWarnings("unchecked")
    private SyncTarget toTarget(EmployerSource binding, Employer employer, JobSourceView source) {
        Map<String, Object> settings = new LinkedHashMap<>(source.settings());
        settings.putAll(json.read(binding.getSettingsJson(), Map.class));
        return new SyncTarget(binding.getId(), binding.getJobSourceId(), employer.getId(), employer.getKey(), source.key(), source.adapterId(),
                binding.getExternalRef(), settings);
    }

    private boolean putBinding(Employer employer, BindingSpec spec, Map<String, UUID> sourceIds) {
        UUID sourceId = sourceIdOf(spec.sourceKey(), sourceIds);
        Optional<EmployerSource> existing = bindings.findByJobSourceIdAndExternalRef(sourceId, spec.externalRef());
        if (existing.isPresent()) {
            EmployerSource binding = existing.get();
            if (!binding.getEmployerId().equals(employer.getId())) {
                String owner = employers.findById(binding.getEmployerId()).map(Employer::getKey).orElse("?");
                throw new DomainRuleViolationException("'" + spec.externalRef() + "' on " + spec.sourceKey()
                        + " is already bound to employer '" + owner + "'");
            }
            return binding.configure(spec.enabled(), spec.priority());
        }
        bindings.save(EmployerSource.builder()
                .employerId(employer.getId())
                .jobSourceId(sourceId)
                .externalRef(spec.externalRef())
                .enabled(spec.enabled())
                .priority(spec.priority().shortValue())
                .build());
        return true;
    }

    private static UUID sourceIdOf(String sourceKey, Map<String, UUID> sourceIds) {
        UUID id = sourceIds.get(sourceKey);
        if (id == null) {
            throw new DomainRuleViolationException("Unknown job source: " + sourceKey);
        }
        return id;
    }

    private Employer load(String key) {
        return employers.findByKey(key).orElseThrow(() -> new NotFoundException("Employer", key));
    }

    private Employer newEmployer(EmployerSpec spec) {
        return Employer.builder()
                .key(spec.key())
                .displayName(spec.displayName())
                .normalizedName(EmployerNames.normalize(spec.displayName()))
                .websiteUrl(spec.website())
                .careersUrl(spec.careersUrl())
                .industry(spec.industry())
                .tags(new ArrayList<>(spec.tags()))
                .headquarters(spec.headquarters())
                .discoveryPriority(spec.discoveryPriority().shortValue())
                .enabled(spec.enabled())
                .metadataJson(spec.metadata() == null ? "{}" : json.write(spec.metadata()))
                .build();
    }

    /** New metadata JSON, {@code null} for "unchanged"; stored jsonb is compared by content, not text. */
    private String metadataJson(EmployerSpec spec, Employer current) {
        if (spec.metadata() == null) {
            return null;
        }
        String wanted = json.write(spec.metadata());
        return json.canonicalize(current.getMetadataJson()).equals(wanted) ? null : wanted;
    }

    private EmployerView view(Employer employer) {
        return view(employer, bindingViews(List.of(employer.getId())).getOrDefault(employer.getId(), List.of()));
    }

    @SuppressWarnings("unchecked")
    private EmployerView view(Employer e, List<BindingView> bindingViews) {
        return new EmployerView(e.getId(), e.getKey(), e.getDisplayName(), e.getNormalizedName(), e.getWebsiteUrl(),
                e.getCareersUrl(), e.getIndustry(), e.getTags(), e.getHeadquarters(), e.getDiscoveryPriority(),
                e.isEnabled(), e.getArchivedAt(), json.read(e.getMetadataJson(), Map.class), bindingViews,
                e.getVersion() == null ? 0 : e.getVersion());
    }

    /** One query for all bindings of the given employers (no N+1). */
    private Map<UUID, List<BindingView>> bindingViews(Collection<UUID> employerIds) {
        if (employerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, JobSourceView> sourceViews = sources.viewsById();
        return bindings.findByEmployerIdIn(employerIds).stream()
                .sorted(Comparator.comparing(EmployerSource::getPriority).reversed()
                        .thenComparing(b -> sourceViews.get(b.getJobSourceId()).key()))
                .collect(Collectors.groupingBy(EmployerSource::getEmployerId, Collectors.mapping(b -> {
                    JobSourceView source = sourceViews.get(b.getJobSourceId());
                    return new BindingView(source.key(), source.adapterId(), b.getExternalRef(), b.isEnabled(), b.getPriority(),
                            b.getLastSuccessAt(), b.getLastFailureAt(), b.getConsecutiveFailures(), b.getLastError());
                }, Collectors.toList())));
    }
}
