package com.jobsapply.platform.preparation;

import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.ApplicationView;
import com.jobsapply.platform.posting.PostingViews;
import com.jobsapply.platform.preparation.PackageContent.PackageIssue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read models of applications and their packages. */
public final class PreparationViews {

    private PreparationViews() {}

    public enum Outcome {
        CREATED,
        NEW_VERSION,
        UNCHANGED,
        NOT_ELIGIBLE,
        EXPIRED
    }

    public record Result(UUID applicationId, ApplicationStatus status, Outcome outcome, Integer packageVersion, List<PackageIssue> issues) {}

    public record PackageView(UUID id, int versionNo, Instant createdAt, int blockingIssues, int warnings, PackageContent content) {}

    public record PackageVersion(UUID id, int versionNo, Instant createdAt, int blockingIssues, int warnings) {}

    public record ApplicationDetail(ApplicationView application, PostingViews.Summary posting, PackageView currentPackage) {}

    public record ApplicationSummary(UUID id, ApplicationStatus status, Instant statusChangedAt, UUID postingId, String postingTitle,
            String employerName, Integer packageVersion) {}

    public record Download(String mediaType, String fileName, byte[] bytes) {}

    /** A resume edit: claims citing fact ids, or text the candidate writes and approves. */
    public record SectionEdit(String heading, List<ClaimEdit> claims) {}

    public record ClaimEdit(String text, List<String> factIds, boolean userApproved) {}
}
