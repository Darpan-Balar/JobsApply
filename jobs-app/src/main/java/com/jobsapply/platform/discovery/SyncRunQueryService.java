package com.jobsapply.platform.discovery;

import com.jobsapply.platform.discovery.internal.SyncErrorRepository;
import com.jobsapply.platform.discovery.internal.SyncRun;
import com.jobsapply.platform.discovery.internal.SyncRunRepository;
import com.jobsapply.platform.employer.BindingRef;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.SyncTarget;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.source.SourceService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncRunQueryService {

    private final SyncRunRepository runs;
    private final SyncErrorRepository errors;
    private final EmployerService employers;
    private final SourceService sources;

    /** Most recent first, optionally for one source and/or one employer. */
    @Transactional(readOnly = true)
    public List<SyncRunView> recent(String sourceKey, String employerKey, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        List<SyncRun> found;
        if (employerKey != null) {
            List<UUID> bindingIds = employers.syncTargets(sourceKey, employerKey).stream().map(SyncTarget::employerSourceId).toList();
            found = bindingIds.isEmpty() ? List.of() : runs.findByEmployerSourceIdInOrderByStartedAtDesc(bindingIds, page);
        } else if (sourceKey != null) {
            UUID sourceId = sources.idsByKey().get(sourceKey);
            found = sourceId == null ? List.of() : runs.findByJobSourceIdOrderByStartedAtDesc(sourceId, page);
        } else {
            found = runs.findByOrderByStartedAtDesc(page);
        }
        Map<UUID, BindingRef> bindings = employers.bindingRefs(found.stream().map(SyncRun::getEmployerSourceId).filter(Objects::nonNull).toList());
        return found.stream().map(run -> view(run, bindings.get(run.getEmployerSourceId()), List.of())).toList();
    }

    @Transactional(readOnly = true)
    public SyncRunView get(UUID id) {
        SyncRun run = runs.findById(id).orElseThrow(() -> new NotFoundException("Sync run", id));
        BindingRef binding = run.getEmployerSourceId() == null ? null
                : employers.bindingRefs(List.of(run.getEmployerSourceId())).get(run.getEmployerSourceId());
        List<SyncRunView.Error> runErrors = errors.findBySyncRunIdOrderByOccurredAtAsc(id).stream()
                .map(e -> new SyncRunView.Error(e.getKind().name(), e.getExternalRef(), e.getMessage(), e.getOccurredAt())).toList();
        return view(run, binding, runErrors);
    }

    private static SyncRunView view(SyncRun run, BindingRef binding, List<SyncRunView.Error> runErrors) {
        return new SyncRunView(run.getId(), binding == null ? null : binding.sourceKey(), binding == null ? null : binding.employerKey(),
                binding == null ? null : binding.externalRef(), run.getTrigger(), run.isDryRun(), run.getStatus(), run.getStartedAt(),
                run.getFinishedAt(), run.counts(), run.getErrorSummary(), runErrors);
    }
}
