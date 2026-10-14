package com.jobsapply.platform.catalog;

import lombok.Builder;

@Builder
public record JobFamilySpec(String key, String displayName, String description) {}
