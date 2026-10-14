package com.jobsapply.spi.application;

import java.net.URI;

/** @param sourceKey the job source the application link came from (e.g. {@code greenhouse}), or {@code null} */
public record ApplicationDestination(URI applicationUrl, String sourceKey) {}
