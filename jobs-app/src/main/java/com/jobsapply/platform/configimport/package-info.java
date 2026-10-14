/**
 * Loads versioned YAML configuration, upgrades older schema versions, validates it (structurally and semantically),
 * and imports it idempotently (ADR-0004). Errors are reported as {@code file:path — message}.
 */
package com.jobsapply.platform.configimport;
