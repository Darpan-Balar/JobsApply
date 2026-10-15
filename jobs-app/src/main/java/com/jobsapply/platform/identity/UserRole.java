package com.jobsapply.platform.identity;

/**
 * {@code ADMIN} may change the shared catalog (employers, sources, configuration, synchronization) and manage
 * accounts. It grants no access to other users' personal data.
 */
public enum UserRole {
    USER,
    ADMIN
}
