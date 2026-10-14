package com.jobsapply.platform.configimport;

import java.util.List;

/**
 * Result of validating or importing a configuration directory.
 *
 * @param applied whether changes were committed (false for validation-only, dry runs and invalid configuration)
 * @param notices informational messages (e.g. kinds validated but not yet importable)
 */
public record ConfigImportReport(
        boolean dryRun, boolean applied, List<ConfigIssue> issues, List<ConfigItemOutcome> outcomes, List<String> notices) {

    public ConfigImportReport {
        issues = List.copyOf(issues);
        outcomes = List.copyOf(outcomes);
        notices = List.copyOf(notices);
    }

    public boolean isValid() {
        return issues.isEmpty();
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append(isValid() ? "Configuration is valid" : "Configuration has " + issues.size() + " problem(s)");
        out.append(dryRun ? " (dry run, no changes written)" : applied ? " (changes applied)" : "").append('\n');
        issues.forEach(issue -> out.append("  ERROR  ").append(issue).append('\n'));
        outcomes.forEach(outcome -> out.append("  ").append(outcome).append('\n'));
        notices.forEach(notice -> out.append("  NOTE   ").append(notice).append('\n'));
        return out.toString();
    }
}
