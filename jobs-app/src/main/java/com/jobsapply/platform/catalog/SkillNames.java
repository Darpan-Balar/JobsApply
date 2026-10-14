package com.jobsapply.platform.catalog;

import java.util.Locale;

public final class SkillNames {

    private SkillNames() {}

    /** Lower-cases, trims and collapses internal whitespace: {@code "  Spring   Boot "} → {@code "spring boot"}. */
    public static String normalize(String name) {
        return name == null ? null : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
