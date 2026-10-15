package com.jobsapply.platform.identity.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code jobs.identity.*}.
 *
 * @param legacyUsername the pre-login single-user account; the first-account setup takes it over so its data is kept
 * @param maxFailedLogins failed sign-ins per username before it is locked for {@code lockout}
 */
@Validated
@ConfigurationProperties("jobs.identity")
public record IdentityProperties(@NotBlank String legacyUsername, @Min(3) @Max(20) int maxFailedLogins, Duration lockout) {

    public IdentityProperties {
        legacyUsername = legacyUsername == null || legacyUsername.isBlank() ? "local" : legacyUsername;
        maxFailedLogins = maxFailedLogins == 0 ? 5 : maxFailedLogins;
        lockout = Objects.requireNonNullElse(lockout, Duration.ofMinutes(15));
    }
}
