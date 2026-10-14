package com.jobsapply.platform.shared.error;

/** Base of expected, business-level failures. Messages must not contain personal data. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
