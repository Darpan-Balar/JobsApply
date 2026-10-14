package com.jobsapply.platform.assist;

import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.spi.application.Blocker;
import java.util.List;
import java.util.UUID;

/**
 * Where browser assistance for one application stands. Field lists carry labels and reasons, never values.
 *
 * @param requiredStillEmpty field ids the form still requires (as last checked)
 */
public record AssistView(UUID applicationId, UUID attemptId, State state, AttemptOutcome outcome, List<Blocker> blockers,
        List<String> humanSteps, List<Field> filled, List<Field> leftToYou, List<String> requiredStillEmpty, String instructions) {

    public AssistView {
        blockers = List.copyOf(blockers);
        humanSteps = List.copyOf(humanSteps);
        filled = List.copyOf(filled);
        leftToYou = List.copyOf(leftToYou);
        requiredStillEmpty = List.copyOf(requiredStillEmpty);
    }

    public enum State {
        /** Something only you can resolve (sign-in, challenge, one-time code, unrecognized page); nothing was filled. */
        PAUSED_FOR_YOU,
        /** Filled what was certain; complete the rest, check everything, and press Submit yourself. */
        READY_FOR_YOU,
        /** No window is open; the attempt is still in progress until you report its outcome. */
        WINDOW_NOT_OPEN,
        CONFIRMED,
        FAILED,
        /** The attempt already ended (replay or verified outcome). */
        FINISHED
    }

    /** @param note where the value came from, or why the field is left to you */
    public record Field(String fieldId, String label, boolean required, String note) {}

    static AssistView of(UUID applicationId, UUID attemptId, State state, AttemptOutcome outcome, String instructions) {
        return new AssistView(applicationId, attemptId, state, outcome, List.of(), List.of(), List.of(), List.of(), List.of(), instructions);
    }
}
