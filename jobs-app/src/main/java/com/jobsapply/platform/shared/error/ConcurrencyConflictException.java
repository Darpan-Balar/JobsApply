package com.jobsapply.platform.shared.error;

/** The client's view of a resource is stale (version mismatch). */
public class ConcurrencyConflictException extends DomainException {

    public ConcurrencyConflictException(String resource, Object key, long expected, long actual) {
        super(resource + " " + key + " was modified concurrently (expected version " + expected + ", current " + actual
                + "); reload and retry");
    }
}
