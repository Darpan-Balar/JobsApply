package com.jobsapply.platform.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What shows that a submission happened. {@code NONE} is not evidence: the outcome is then unconfirmed.
 *
 * @param detail e.g. the confirmation message or e-mail subject (may contain personal data; stored encrypted)
 */
public record SubmissionEvidence(@NotNull Kind kind, @Size(max = 4000) String detail, @Size(max = 2048) String pageUrl) {

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
