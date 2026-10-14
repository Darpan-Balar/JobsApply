package com.jobsapply.platform.assist.internal;

import com.jobsapply.platform.shared.storage.DocumentStore;
import com.jobsapply.spi.application.ApplicationDestination;
import com.jobsapply.spi.application.ApplicationSession;
import java.util.UUID;

/** The platform's side of an assistance window. Artifacts are stored encrypted for the owner (write-once files). */
public record PlatformSession(UUID id, UUID applicationId, ApplicationDestination destination, UUID owner, DocumentStore store)
        implements ApplicationSession {

    public static final int MAX_ARTIFACT_BYTES = 10 * 1024 * 1024;

    @Override
    public String saveArtifact(String mediaType, byte[] content) {
        if (!"image/png".equals(mediaType) || content.length > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("only PNG screenshots up to 10 MB are stored");
        }
        return store.put(owner, content);
    }

    @Override
    public String toString() {
        return "PlatformSession[" + id + ", application " + applicationId + "]";
    }
}
