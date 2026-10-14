package com.jobsapply.platform.configimport;

import tools.jackson.databind.node.ObjectNode;

/**
 * Upgrades one document kind from {@link #fromVersion()} to {@code fromVersion() + 1}. Upgraders form a chain (one per
 * version step) and are applied in order before structural validation. Register implementations as Spring beans.
 */
public interface ConfigUpgrader {

    ConfigKind kind();

    int fromVersion();

    /** Returns the upgraded document body (envelope fields already removed). Must not mutate {@code document}. */
    ObjectNode upgrade(ObjectNode document);
}
