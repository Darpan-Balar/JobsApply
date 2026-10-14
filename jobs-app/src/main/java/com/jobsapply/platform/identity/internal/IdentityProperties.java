package com.jobsapply.platform.identity.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code jobs.identity.*}.
 *
 * @param mode {@code LOCAL}: every API request acts as {@code local-user} (single-user, localhost-only deployment).
 *     Authenticated multi-user login replaces this in Phase 8.
 */
@Validated
@ConfigurationProperties("jobs.identity")
public record IdentityProperties(Mode mode, @NotNull @Valid LocalUser localUser) {

    public enum Mode {
        LOCAL
    }

    public IdentityProperties {
        mode = mode == null ? Mode.LOCAL : mode;
    }

    public record LocalUser(@NotBlank String username, @NotBlank String displayName) {}
}
