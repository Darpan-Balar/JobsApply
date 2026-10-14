package com.jobsapply.platform.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What shows that a submission happened. {@code NONE} is not evidence: the outcome is then unconfirmed.
 *
 * @param detail e.g. the confirmation message or e-mail subject (may contain personal data; stored encrypted)
 * @param artifactRef stored artifact (e.g. a confirmation screenshot taken during browser assistance), or {@code null}
 */
public record SubmissionEvidence(@NotNull Kind kind, @Size(max = 4000) String detail, @Size(max = 2048) String pageUrl,
        @Size(max = 200) String artifactRef) {

    public SubmissionEvidence(Kind kind, String detail, String pageUrl) {
        this(kind, detail, pageUrl, null);
    }

    public enum Kind {
        CONFIRMATION_PAGE,
        CONFIRMATION_EMAIL,
        SCREENSHOT,
        EMPLOYER_PORTAL,
        NONE
    }

    public boolean credible() {
        return kind != Kind.NONE && detail != null && !detail.isBlank();
    }

    @Override
    public String toString() {
        return "SubmissionEvidence[kind=" + kind + ", redacted]";
    }
}
