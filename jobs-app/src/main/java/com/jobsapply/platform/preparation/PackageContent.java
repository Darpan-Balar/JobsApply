package com.jobsapply.platform.preparation;

import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.Eligibility;
import com.jobsapply.spi.matching.JobSnapshot;
import com.jobsapply.spi.render.AnswerDraft;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.ResumeDocument;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything one prepared application consists of. Stored encrypted and immutable; a change creates a new package
 * version. Contains personal data — never log it.
 */
public record PackageContent(
        Instant generatedAt,
        JobSnapshot job,
        Destination destination,
        String employerName,
        ProfileRef candidate,
        ProfileRef role,
        MatchSummary match,
        ResumeTemplateSpec template,
        ResumeDocument resume,
        CoverNote coverNote,
        List<ApplicationQuestion> standardFields,
        List<Answer> answers,
        List<DocumentRef> documents,
        AvailabilitySummary availability,
        List<PackageIssue> issues) {

    public PackageContent {
        standardFields = List.copyOf(standardFields);
        answers = List.copyOf(answers);
        documents = List.copyOf(documents);
        issues = List.copyOf(issues);
    }

    public record Destination(UUID listingId, String sourceKey, String applicationUrl) {}

    public record ProfileRef(String key, UUID versionId, int versionNo) {}

    public record MatchSummary(UUID matchId, BigDecimal score, Eligibility eligibility, List<String> uncertain, List<String> reasons) {}

    public record CoverNote(String greeting, List<Claim> claims, String closing) {}

    public record Answer(ApplicationQuestion question, AnswerDraft draft) {}

    public record DocumentRef(UUID id, String kind, String mediaType, long sizeBytes, String sha256) {}

    public record AvailabilitySummary(Availability availability, List<String> checks) {}

    public record PackageIssue(Severity severity, String code, String message) {
        public enum Severity {
            /** Must be fixed before the package can be reviewed for submission. */
            BLOCKING,
            WARNING
        }
    }

    public long blockingIssues() {
        return issues.stream().filter(i -> i.severity() == PackageIssue.Severity.BLOCKING).count();
    }

    public List<Answer> unanswered() {
        return answers.stream().filter(a -> a.draft().status() == AnswerDraft.Status.NEEDS_USER_INPUT).toList();
    }

    public PackageContent withDocuments(List<DocumentRef> docs) {
        return new PackageContent(generatedAt, job, destination, employerName, candidate, role, match, template, resume, coverNote,
                standardFields, answers, docs, availability, issues);
    }

    @Override
    public String toString() {
        return "PackageContent[redacted]";
    }
}
