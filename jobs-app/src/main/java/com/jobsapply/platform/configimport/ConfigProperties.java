package com.jobsapply.platform.configimport;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jobs.config.*}.
 *
 * @param directory configuration directory (relative paths resolve against the working directory)
 * @param importOnStartup import system configuration when the application starts; startup fails on invalid config
 * @param validateOnly validate the configuration, print a report and exit (non-zero on problems)
 */
@ConfigurationProperties("jobs.config")
public record ConfigProperties(Path directory, boolean importOnStartup, boolean validateOnly) {

    public ConfigProperties {
        directory = directory == null ? Path.of("config") : directory;
    }
}
