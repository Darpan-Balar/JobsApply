package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.VerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/**
 * A scalar fact or preference, e.g. {@code work-authorization.us = citizen}, {@code notice-period-days = 30}. Legal
 * and compensation attributes are only ever supplied by the candidate, never derived.
 */
@Builder(toBuilder = true)
public record CandidateAttribute(
        @NotBlank @Pattern(regexp = Keys.FACT_ID_REGEX) String id,
        @NotNull VerificationStatus verification,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{0,99}$") String key,
        @NotBlank String value) implements CandidateFact {}
