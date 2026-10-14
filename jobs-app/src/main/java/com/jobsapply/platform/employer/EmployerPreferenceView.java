package com.jobsapply.platform.employer;

public record EmployerPreferenceView(String employerKey, String employerName, PreferenceMode mode, int priority, String note) {}
