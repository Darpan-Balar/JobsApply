package com.jobsapply.platform.application;

import static com.jobsapply.platform.application.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

/** Exhaustive check of the transition table against the specification in ADR-0006. */
class ApplicationStatusTest {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> EXPECTED = Map.ofEntries(
            Map.entry(DISCOVERED, EnumSet.of(MATCHED, NOT_ELIGIBLE, REJECTED_BY_USER, EXPIRED)),
            Map.entry(MATCHED, EnumSet.of(PREPARED, NOT_ELIGIBLE, REJECTED_BY_USER, EXPIRED)),
            Map.entry(NOT_ELIGIBLE, EnumSet.of(MATCHED, REJECTED_BY_USER, EXPIRED)),
            Map.entry(PREPARED, EnumSet.of(READY_FOR_REVIEW, REJECTED_BY_USER, EXPIRED)),
            Map.entry(READY_FOR_REVIEW, EnumSet.of(APPROVED, PREPARED, REJECTED_BY_USER, EXPIRED)),
            Map.entry(APPROVED, EnumSet.of(SUBMITTING, PREPARED, REJECTED_BY_USER, EXPIRED)),
            Map.entry(SUBMITTING, EnumSet.of(SUBMITTED, FAILED, SUBMISSION_UNCONFIRMED)),
            Map.entry(SUBMISSION_UNCONFIRMED, EnumSet.of(SUBMITTED, FAILED)),
            Map.entry(FAILED, EnumSet.of(PREPARED, REJECTED_BY_USER, EXPIRED)),
            Map.entry(SUBMITTED, EnumSet.of(WITHDRAWN)),
            Map.entry(EXPIRED, EnumSet.noneOf(ApplicationStatus.class)),
            Map.entry(REJECTED_BY_USER, EnumSet.noneOf(ApplicationStatus.class)),
            Map.entry(WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class)));

    @Test
    void everyStateIsSpecified() {
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrder(ApplicationStatus.values());
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void everyPairMatchesTheSpecification(ApplicationStatus from) {
        for (ApplicationStatus to : ApplicationStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(EXPECTED.get(from).contains(to));
        }
    }

    @Test
    void submissionCannotBeAbandonedOrExpiredMidFlight() {
        assertThat(SUBMITTING.allowedTargets()).doesNotContain(EXPIRED, REJECTED_BY_USER, PREPARED, APPROVED);
        assertThat(SUBMISSION_UNCONFIRMED.allowedTargets())
                .as("an unconfirmed submission is never retried: it can only be resolved")
                .containsExactlyInAnyOrder(SUBMITTED, FAILED);
    }

    @Test
    void noStateCanReachSubmittingExceptApproved() {
        for (ApplicationStatus from : ApplicationStatus.values()) {
            assertThat(from.canTransitionTo(SUBMITTING)).as(from.name()).isEqualTo(from == APPROVED);
        }
    }

    @Test
    void terminalStatesAreExactlyTheClosedOnes() {
        assertThat(EnumSet.allOf(ApplicationStatus.class).stream().filter(ApplicationStatus::isTerminal))
                .containsExactlyInAnyOrder(EXPIRED, REJECTED_BY_USER, WITHDRAWN);
        assertThat(EnumSet.allOf(ApplicationStatus.class).stream().filter(ApplicationStatus::isClosed))
                .containsExactlyInAnyOrder(EXPIRED, REJECTED_BY_USER, WITHDRAWN);
    }

    @Test
    void liveStatesMatchTheDuplicateSubmissionIndex() {
        assertThat(EnumSet.allOf(ApplicationStatus.class).stream().filter(ApplicationStatus::isLive))
                .containsExactlyInAnyOrder(APPROVED, SUBMITTING, SUBMITTED, SUBMISSION_UNCONFIRMED);
    }

    @Test
    void humanDecisionsRequireAUser() {
        assertThat(READY_FOR_REVIEW.requiresUserActor(APPROVED)).isTrue();
        assertThat(MATCHED.requiresUserActor(REJECTED_BY_USER)).isTrue();
        assertThat(SUBMITTED.requiresUserActor(WITHDRAWN)).isTrue();
        assertThat(SUBMISSION_UNCONFIRMED.requiresUserActor(SUBMITTED)).isTrue();
        assertThat(SUBMISSION_UNCONFIRMED.requiresUserActor(FAILED)).isTrue();
        assertThat(SUBMITTING.requiresUserActor(SUBMISSION_UNCONFIRMED)).isFalse();
        assertThat(DISCOVERED.requiresUserActor(MATCHED)).isFalse();
    }
}
