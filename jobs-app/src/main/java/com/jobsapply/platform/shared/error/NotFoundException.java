package com.jobsapply.platform.shared.error;

public class NotFoundException extends DomainException {

    public NotFoundException(String resource, Object key) {
        super(resource + " not found: " + key);
    }
}
