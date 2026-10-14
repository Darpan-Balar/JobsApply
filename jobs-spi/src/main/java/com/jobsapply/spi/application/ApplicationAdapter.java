package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Plugin;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.ValidationReport;
import java.time.Duration;

/** Assists the human with an application form on a specific kind of destination. */
public interface ApplicationAdapter extends Plugin {

    boolean supports(ApplicationDestination destination);

    PluginResult<FormInspection> inspect(ApplicationSession session);

    PluginResult<FillReport> fill(ApplicationSession session, FieldMappingPlan plan);

    PluginResult<ValidationReport> validate(ApplicationSession session);

    /** Waits up to {@code timeout} for the human's submission and reports what was observed. */
    PluginResult<ConfirmationEvidence> observeConfirmation(ApplicationSession session, Duration timeout);
}
