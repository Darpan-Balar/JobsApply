package com.jobsapply.platform.shared.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code jobs.storage.*}. @param directory where encrypted files live (git-ignored {@code data/} by default) */
@ConfigurationProperties("jobs.storage")
public record StorageProperties(Path directory) {

    public StorageProperties {
        directory = directory == null ? Path.of("data") : directory;
    }
}
