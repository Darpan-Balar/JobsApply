package com.jobsapply.spi.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Citation of a candidate fact within a specific, immutable candidate profile version. */
public record FactRef(@NotNull UUID candidateProfileVersionId, @NotBlank String factId) {}
