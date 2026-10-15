package com.jobsapply.platform.export;

import com.jobsapply.platform.application.ApplicationService;
import com.jobsapply.platform.application.ApplicationService.ApprovalView;
import com.jobsapply.platform.application.ApplicationService.AttemptView;
import com.jobsapply.platform.audit.AuditService;
import com.jobsapply.platform.candidate.CandidateProfileDetail;
import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.candidate.CandidateProfileVersionRef;
import com.jobsapply.platform.candidate.MasterResumeService;
import com.jobsapply.platform.employer.EmployerPreferenceService;
import com.jobsapply.platform.employer.EmployerPreferenceView;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserSummary;
import com.jobsapply.platform.preparation.PreparationService;
import com.jobsapply.platform.preparation.PreparationViews;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.tracking.TrackingService;
import com.jobsapply.platform.tracking.TrackingViews;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the signed-in user's export. Every part is read through owner-scoped services, so an export can only ever
 * contain the requesting user's own data. Generated documents and master resumes are listed with their checksums;
 * their bytes are downloaded separately.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    public static final int FORMAT_VERSION = 1;
    private static final int AUDIT_LIMIT = 10_000;

    private final UserService users;
    private final CandidateProfileService profiles;
    private final MasterResumeService masterResumes;
    private final PreparationService preparation;
    private final ApplicationService applications;
    private final TrackingService tracking;
    private final EmployerPreferenceService preferences;
    private final AuditService audit;
    private final Clock clock;

    /** Personal data: never log it. */
    public record Export(int formatVersion, Instant exportedAt, Account account, List<Profile> candidateProfiles,
            List<Application> applications, Tracking tracking, List<EmployerPreferenceView> employerPreferences,
            List<AuditService.AuditView> audit) {
        @Override
        public String toString() {
            return "Export[redacted]";
        }
    }

    public record Account(UUID id, String username, String displayName, String email, String role) {}

    public record Profile(CandidateProfileDetail detail, List<CandidateProfileVersionRef> versions,
            List<MasterResumeService.MasterResumeView> masterResumes) {}

    public record Application(PreparationViews.ApplicationDetail detail, List<PreparationViews.PackageVersion> packageVersions,
            List<ApprovalView> approvals, List<AttemptView> attempts) {}

    public record Tracking(List<TrackingViews.ContactView> contacts, List<TrackingViews.InterviewView> interviews,
            List<TrackingViews.FollowUpView> followUps) {}

    public Export export() {
        UserSummary me = users.get(UserContext.requireUserId());
        List<Profile> candidateProfiles = profiles.listProfiles().stream()
                .map(p -> new Profile(profiles.detail(p.key()), profiles.versions(p.key()), masterResumes.list(p.key())))
                .toList();
        List<Application> apps = preparation.list().stream()
                .map(a -> new Application(preparation.detail(a.id()), preparation.versions(a.id()), applications.approvals(a.id()),
                        applications.attempts(a.id())))
                .toList();
        return new Export(FORMAT_VERSION, clock.instant(),
                new Account(me.id(), me.username(), me.displayName(), me.email(), me.role().name()),
                candidateProfiles, apps,
                new Tracking(tracking.contacts(), tracking.interviews(null), tracking.followUps(true, null)),
                preferences.list(), audit.mine(null, AUDIT_LIMIT));
    }
}
