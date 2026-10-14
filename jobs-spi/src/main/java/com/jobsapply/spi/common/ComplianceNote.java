package com.jobsapply.spi.common;

import java.net.URI;

/**
 * What a plugin relies on, so its use can be reviewed: the official documentation it implements, whether it needs
 * authentication, and any terms-of-use notes (rate limits, permitted use).
 */
public record ComplianceNote(URI documentation, boolean authenticationRequired, String termsNotes) {}
