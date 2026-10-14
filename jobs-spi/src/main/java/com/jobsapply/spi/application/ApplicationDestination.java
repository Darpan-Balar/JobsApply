package com.jobsapply.spi.application;

import java.net.URI;

public record ApplicationDestination(URI applicationUrl, String sourceKey) {}
