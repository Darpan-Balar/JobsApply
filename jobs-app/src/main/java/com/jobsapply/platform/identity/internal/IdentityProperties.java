package com.jobsapply.platform.identity.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code jobs.identity.*}. Until authentication exists (Phase 8) the platform acts as {@code local-user}, which is
 * created on startup if missing.
 */
@Validated
@ConfigurationProperties("jobs.identity")
public record IdentityProperties(@NotNull @Valid LocalUser localUser) {

    public record LocalUser(@NotBlank String username, @NotBlank String displayName) {}
}
