package com.jobsapply.platform.source;

import java.util.List;

/**
 * Result of checking a source specification against the installed plugins.
 *
 * @param issues blocking problems, as {@code path: message} pairs relative to the source
 * @param notices non-blocking information (e.g. a disabled source whose adapter is not installed)
 */
public record SourceCheck(List<Issue> issues, List<String> notices) {

    public record Issue(String path, String message) {}

    public SourceCheck {
        issues = List.copyOf(issues);
        notices = List.copyOf(notices);
    }

    public boolean isValid() {
        return issues.isEmpty();
    }
}
