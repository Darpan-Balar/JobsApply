package com.jobsapply.spi.common;

import java.util.List;

/** Result of validating configuration or a form; empty {@code issues} means valid. */
public record ValidationReport(List<Issue> issues) {

    /** @param path dotted path of the offending element (e.g. {@code settings.boardToken}) */
    public record Issue(String path, String message) {}

    public ValidationReport {
        issues = Lists.immutable(issues);
    }

    public static ValidationReport valid() {
        return new ValidationReport(List.of());
    }

    public boolean isValid() {
        return issues.isEmpty();
    }
}
