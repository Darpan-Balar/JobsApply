package com.jobsapply.platform.employer;

import java.util.UUID;

public record BindingRef(UUID employerSourceId, String employerKey, String sourceKey, String externalRef) {}
