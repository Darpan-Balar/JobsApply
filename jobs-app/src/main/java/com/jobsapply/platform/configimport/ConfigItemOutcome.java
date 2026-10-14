package com.jobsapply.platform.configimport;

/** What happened to one configured item, e.g. {@code RoleProfile backend-engineer: NEW_VERSION}. */
public record ConfigItemOutcome(ConfigKind kind, String key, String outcome) {

    @Override
    public String toString() {
        return kind.yamlName() + " " + key + ": " + outcome;
    }
}
