package com.jobsapply.platform.shared.error;

/** A request conflicts with a business rule or with existing state (e.g. a duplicate key). */
public class DomainRuleViolationException extends DomainException {

    public DomainRuleViolationException(String message) {
        super(message);
    }

    public DomainRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
