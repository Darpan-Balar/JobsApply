package com.jobsapply.platform.application;

import com.jobsapply.platform.application.internal.Application;
import com.jobsapply.platform.application.internal.ApplicationRepository;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.tenancy.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the application aggregate (the current user's applications). State changes go through
 * {@link Application#transitionTo}; callers join the surrounding transaction. Approval and submission arrive in Phase 6.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final EnumSet<ApplicationStatus> CLOSED = EnumSet.of(
            ApplicationStatus.REJECTED_BY_USER, ApplicationStatus.WITHDRAWN, ApplicationStatus.EXPIRED);

    private final ApplicationRepository applications;
    private final Clock clock;

    /** The open (not closed) application for this posting and candidate profile, or a new DISCOVERED one. */
    @Transactional
    public ApplicationView openOrCreate(UUID postingId, UUID candidateProfileId, UUID candidateVersionId, UUID roleVersionId) {
        UserContext.requireUserId();
        Application application = applications.findOpen(postingId, candidateProfileId, CLOSED)
                .orElseGet(() -> applications.save(Application.discover(postingId, candidateProfileId, candidateVersionId,
                        roleVersionId, Actor.system(), Instant.now(clock))));
        return view(application);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationView> findOpen(UUID postingId, UUID candidateProfileId) {
        UserContext.requireUserId();
        return applications.findOpen(postingId, candidateProfileId, CLOSED).map(ApplicationService::view);
    }

    @Transactional
    public ApplicationView transition(UUID id, ApplicationStatus target, Actor actor, String reason) {
        Application application = load(id);
        if (application.getStatus() != target) {
            application.transitionTo(target, actor, reason, Instant.now(clock));
        }
        applications.flush();
        return view(application);
    }

    @Transactional
    public void recordPreparation(UUID id, UUID candidateVersionId, UUID roleVersionId, UUID matchId, UUID listingId, UUID packageId) {
        Application application = load(id);
        application.useInputs(candidateVersionId, roleVersionId, matchId);
        application.useDestination(listingId);
        application.attachPackage(packageId);
    }

    @Transactional(readOnly = true)
    public ApplicationView get(UUID id) {
        return view(load(id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list() {
        UserContext.requireUserId();
        return applications.findAllByOrderByStatusChangedAtDesc().stream().map(ApplicationService::view).toList();
    }

    private Application load(UUID id) {
        UserContext.requireUserId();
        return applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id));
    }

    private static ApplicationView view(Application a) {
        return new ApplicationView(a.getId(), a.getJobPostingId(), a.getJobListingId(), a.getCandidateProfileId(),
                a.getCandidateProfileVersionId(), a.getRoleProfileVersionId(), a.getJobMatchId(), a.getCurrentPackageId(),
                a.getStatus(), a.getStatusChangedAt(),
                a.getHistory().stream().map(h -> new ApplicationView.HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getActorType(),
                        h.getReason(), h.getOccurredAt())).toList());
    }
}
