package com.jobsapply.platform.employer;

import com.jobsapply.spi.common.Keys;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An employer's presence on a job source.
 *
 * @param externalRef the provider's identifier for the employer (board token, site name, ...)
 */
public record BindingSpec(
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String sourceKey,
        @NotBlank @Size(max = 200) String externalRef,
        @NotNull Boolean enabled,
        @NotNull @Min(0) @Max(100) Integer priority) {

    public static BindingSpec of(String sourceKey, String externalRef) {
        return new BindingSpec(sourceKey, externalRef, true, 50);
    }
}
