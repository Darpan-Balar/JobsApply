package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.ValidationReport;
import java.time.Duration;

/**
 * Assists the human with an application form on a specific kind of destination, in a window the human can see.
 *
 * <p>There is deliberately no submit operation: the human presses the final submit button and the adapter only
 * observes the outcome (ADR-0006). On CAPTCHA, MFA, login walls or access restrictions the adapter reports a
 * {@link Blocker} and does nothing else on that page — it never tries to get past them.
 *
 * <p>Operations on one session are never called concurrently by the platform; different sessions may be.
 */
public interface ApplicationAdapter extends Plugin {

    boolean supports(ApplicationDestination destination);

    /** Opens a window at the destination for this session and inspects the form. */
    PluginResult<FormInspection> open(ApplicationSession session);

    /** Inspects the current page again (e.g. after the human signed in or dismissed a dialog). */
    PluginResult<FormInspection> inspect(ApplicationSession session);

    /** Fills only {@link FieldMappingPlan.Confidence#HIGH} mappings; never touches anything else on the page. */
    PluginResult<FillReport> fill(ApplicationSession session, FieldMappingPlan plan);

    /** Reports required fields that are still empty and messages the form itself shows. */
    PluginResult<ValidationReport> validate(ApplicationSession session);

    /** Waits up to {@code timeout} for a confirmation after the human submitted, and reports what was observed. */
    PluginResult<ConfirmationEvidence> observeConfirmation(ApplicationSession session, Duration timeout);

    /** Closes the session's window, if open. Idempotent. */
    void close(ApplicationSession session);
}
