package com.jobsapply.spi.application;

import java.util.UUID;

/** Opaque handle to an open, human-visible application form, owned by the platform. */
public interface ApplicationSession {

    UUID applicationId();

    ApplicationDestination destination();

    boolean isOpen();
}
