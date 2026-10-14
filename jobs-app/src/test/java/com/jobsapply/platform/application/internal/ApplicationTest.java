package com.jobsapply.platform.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.shared.error.InvalidStateTransitionException;
import com.jobsapply.platform.shared.model.Actor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final Instant T0 = Instant.parse("2026-10-14T10:00:00Z");
    private final UUID user = UUID.randomUUID();

    private Application newApplication() {
        return Application.discover(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Actor.system(), T0);
    }

    @Test
    void startsDiscoveredWithInitialHistory() {
        Application application = newApplication();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DISCOVERED);
        assertThat(application.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(ApplicationStatus.DISCOVERED);
        });
    }

    @Test
    void validTransitionsUpdateStatusAndAppendHistory() {
        Application application = newApplication();
        application.transitionTo(ApplicationStatus.MATCHED, Actor.system(), "score 81", T0.plusSeconds(1));
        application.transitionTo(ApplicationStatus.PREPARED, Actor.system(), null, T0.plusSeconds(2));
        application.transitionTo(ApplicationStatus.READY_FOR_REVIEW, Actor.system(), null, T0.plusSeconds(3));
        application.transitionTo(ApplicationStatus.APPROVED, Actor.user(user), "looks good", T0.plusSeconds(4));

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(application.getStatusChangedAt()).isEqualTo(T0.plusSeconds(4));
        assertThat(application.getHistory()).hasSize(5);
        assertThat(application.getHistory().getLast().getActorUserId()).isEqualTo(user);
    }

    @Test
    void invalidTransitionIsRejectedAndStateUnchanged() {
        Application application = newApplication();
        assertThatThrownBy(() -> application.transitionTo(ApplicationStatus.SUBMITTED, Actor.system(), null, T0))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DISCOVERED);
        assertThat(application.getHistory()).hasSize(1);
    }

    @Test
    void systemCannotApproveOnTheUsersBehalf() {
        Application application = newApplication();
        application.transitionTo(ApplicationStatus.MATCHED, Actor.system(), null, T0);
        application.transitionTo(ApplicationStatus.PREPARED, Actor.system(), null, T0);
        application.transitionTo(ApplicationStatus.READY_FOR_REVIEW, Actor.system(), null, T0);

        assertThatThrownBy(() -> application.transitionTo(ApplicationStatus.APPROVED, Actor.system(), null, T0))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("explicit user action");
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
    }

    @Test
    void systemCannotResolveAnUnconfirmedSubmission() {
        Application application = newApplication();
        for (ApplicationStatus step : new ApplicationStatus[] {
            ApplicationStatus.MATCHED, ApplicationStatus.PREPARED, ApplicationStatus.READY_FOR_REVIEW}) {
            application.transitionTo(step, Actor.system(), null, T0);
        }
        application.transitionTo(ApplicationStatus.APPROVED, Actor.user(user), null, T0);
        application.transitionTo(ApplicationStatus.SUBMITTING, Actor.user(user), null, T0);
        application.transitionTo(ApplicationStatus.SUBMISSION_UNCONFIRMED, Actor.system(), "crash recovery", T0);

        assertThatThrownBy(() -> application.transitionTo(ApplicationStatus.SUBMITTED, Actor.system(), null, T0))
                .isInstanceOf(InvalidStateTransitionException.class);
        application.transitionTo(ApplicationStatus.SUBMITTED, Actor.user(user), "verified confirmation email", T0);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
    }
}
