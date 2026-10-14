package com.jobsapply.spi.application;

import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.render.ApplicationQuestion;
import java.net.URI;
import java.util.List;

/**
 * What is on the page: the form fields (as questions whose {@code id} is the field key the adapter fills by), anything
 * that stops assistance ({@code blockers}), and steps only the human can do on an otherwise usable form
 * ({@code humanSteps}, e.g. a CAPTCHA widget next to the submit button).
 */
public record FormInspection(URI pageUrl, List<ApplicationQuestion> fields, List<Blocker> blockers, List<String> humanSteps) {

    public FormInspection {
        fields = Lists.immutable(fields);
        blockers = Lists.immutable(blockers);
        humanSteps = Lists.immutable(humanSteps);
    }

    public boolean blocked() {
        return !blockers.isEmpty();
    }
}
