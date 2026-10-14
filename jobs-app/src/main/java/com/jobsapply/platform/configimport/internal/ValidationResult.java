package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.configimport.ConfigIssue;
import java.util.List;

/** Blocking issues plus non-blocking notices. */
public record ValidationResult(List<ConfigIssue> issues, List<String> notices) {

    public ValidationResult {
        issues = List.copyOf(issues);
        notices = List.copyOf(notices);
    }
}
