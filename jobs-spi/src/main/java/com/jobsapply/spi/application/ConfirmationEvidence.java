package com.jobsapply.spi.application;

import java.net.URI;
import java.time.Instant;

/**
 * What was observed after the human submitted. {@link Kind#NONE} means no credible evidence: the platform records the
 * application as {@code SUBMISSION_UNCONFIRMED}, never as submitted.
 *
 * @param artifactRef platform reference to a stored artifact (e.g. screenshot), or {@code null}
 */
public record ConfirmationEvidence(Kind kind, String detail, URI pageUrl, String artifactRef, Instant observedAt) {

    public enum Kind {
        CONFIRMATION_PAGE,
        CONFIRMATION_EMAIL,
        SCREENSHOT,
        NONE
    }
}
