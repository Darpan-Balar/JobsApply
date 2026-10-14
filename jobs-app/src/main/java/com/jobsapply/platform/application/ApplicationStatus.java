package com.jobsapply.platform.application;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Application lifecycle (ADR-0006). The transition table is the single source of truth for allowed moves; the
 * database's partial unique indexes mirror {@link #isLive()} and {@link #isClosed()}.
 */
public enum ApplicationStatus {
    /** A (user, posting) candidate pair exists; nothing evaluated yet. */
    DISCOVERED,
    /** Evaluated and at or above the role profile's minimum threshold. */
    MATCHED,
    /** A hard eligibility rule failed. Re-evaluation (e.g. new profile version) may move it back to MATCHED. */
    NOT_ELIGIBLE,
    /** An application package was generated. */
    PREPARED,
    /** Package validated and availability rechecked; awaiting the user's review. */
    READY_FOR_REVIEW,
    /** The user approved this exact package for one submission. */
    APPROVED,
    /** Submission in progress; persisted before any external action. */
    SUBMITTING,
    /** Credible confirmation evidence was recorded. */
    SUBMITTED,
    /** Outcome unknown. Never retried automatically; resolved only by recorded manual verification. */
    SUBMISSION_UNCONFIRMED,
    /** A definite, evidenced failure. May be re-prepared (requiring a fresh approval). */
    FAILED,
    /** The source authoritatively reported the vacancy closed. */
    EXPIRED,
    /** The user decided not to apply. */
    REJECTED_BY_USER,
    /** The user withdrew a submitted application. */
    WITHDRAWN;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = new EnumMap<>(ApplicationStatus.class);

    static {
        allow(DISCOVERED, MATCHED, NOT_ELIGIBLE, REJECTED_BY_USER, EXPIRED);
        allow(MATCHED, PREPARED, NOT_ELIGIBLE, REJECTED_BY_USER, EXPIRED);
        allow(NOT_ELIGIBLE, MATCHED, REJECTED_BY_USER, EXPIRED);
        allow(PREPARED, READY_FOR_REVIEW, REJECTED_BY_USER, EXPIRED);
        allow(READY_FOR_REVIEW, APPROVED, PREPARED, REJECTED_BY_USER, EXPIRED);
        allow(APPROVED, SUBMITTING, PREPARED, REJECTED_BY_USER, EXPIRED);
        // Once a submission has started, its outcome must be resolved: no expiry or rejection shortcuts.
        allow(SUBMITTING, SUBMITTED, FAILED, SUBMISSION_UNCONFIRMED);
        allow(SUBMISSION_UNCONFIRMED, SUBMITTED, FAILED);
        allow(FAILED, PREPARED, REJECTED_BY_USER, EXPIRED);
        allow(SUBMITTED, WITHDRAWN);
        allow(EXPIRED);
        allow(REJECTED_BY_USER);
        allow(WITHDRAWN);
    }

    private static void allow(ApplicationStatus from, ApplicationStatus... targets) {
        Set<ApplicationStatus> set = targets.length == 0 ? EnumSet.noneOf(ApplicationStatus.class) : EnumSet.of(targets[0], targets);
        TRANSITIONS.put(from, Collections.unmodifiableSet(set));
    }

    public Set<ApplicationStatus> allowedTargets() {
        return TRANSITIONS.get(this);
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return allowedTargets().contains(target);
    }

    /** No outgoing transitions. */
    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }

    /**
     * Whether moving from this state to {@code target} is a human decision that the system must never take on its
     * own: approving, rejecting, withdrawing, and resolving an unconfirmed submission.
     */
    public boolean requiresUserActor(ApplicationStatus target) {
        return target == APPROVED || target == REJECTED_BY_USER || target == WITHDRAWN || this == SUBMISSION_UNCONFIRMED;
    }

    /** Approved, in flight or submitted: at most one per user and canonical posting (database-enforced). */
    public boolean isLive() {
        return this == APPROVED || this == SUBMITTING || this == SUBMITTED || this == SUBMISSION_UNCONFIRMED;
    }

    /** Closed without an active submission; does not block a fresh application. */
    public boolean isClosed() {
        return this == REJECTED_BY_USER || this == WITHDRAWN || this == EXPIRED;
    }
}
