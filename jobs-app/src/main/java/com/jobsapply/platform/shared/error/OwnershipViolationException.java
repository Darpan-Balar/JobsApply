package com.jobsapply.platform.shared.error;

/** An owned entity was loaded outside its owner's context. Reported to clients as "not found". */
public class OwnershipViolationException extends DomainException {

    public OwnershipViolationException(String entity) {
        super(entity + " is not accessible in the current user context");
    }
}
