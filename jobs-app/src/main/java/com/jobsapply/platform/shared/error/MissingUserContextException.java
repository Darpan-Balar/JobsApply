package com.jobsapply.platform.shared.error;

/** User-owned data was accessed without a bound user. Indicates a programming error; never a silent fallback. */
public class MissingUserContextException extends DomainException {

    public MissingUserContextException() {
        super("No user context is bound; user-owned data cannot be accessed");
    }
}
