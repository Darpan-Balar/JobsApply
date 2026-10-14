package com.jobsapply.spi.common;

/** Shared identifier formats. Kept as compile-time constants so they can be used in validation annotations. */
public final class Keys {

    /** Stable business key: lower-case slug, 1–63 chars (e.g. {@code backend-engineer}, {@code cpp}). */
    public static final String KEY_REGEX = "^[a-z0-9][a-z0-9-]{0,62}$";

    /** Plugin identifier, e.g. {@code greenhouse-job-board}. */
    public static final String PLUGIN_ID_REGEX = "^[a-z0-9][a-z0-9.-]{1,99}$";

    /** Candidate fact identifier, stable across profile versions, e.g. {@code exp-acme-2021}. */
    public static final String FACT_ID_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$";

    private Keys() {}
}
