package com.jobsapply.spi.application;

import java.util.UUID;

/**
 * One browser-assisted attempt at one application, created and owned by the platform. Adapters key their window
 * state by {@link #id()}.
 */
public interface ApplicationSession {

    UUID id();

    UUID applicationId();

    ApplicationDestination destination();

    /**
     * Stores an artifact (e.g. a confirmation screenshot) encrypted on the platform side.
     *
     * @return the reference to put into {@link ConfirmationEvidence#artifactRef()}
     */
    String saveArtifact(String mediaType, byte[] content);
}
