package com.jobsapply.platform.source;

import com.jobsapply.spi.common.Keys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;

/** Desired state of a job source. {@code settings} are validated against the adapter's settings record. */
@Builder(toBuilder = true)
public record JobSourceSpec(
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
        @NotBlank @Pattern(regexp = Keys.PLUGIN_ID_REGEX) String adapterId,
        @NotBlank @Size(max = 200) String displayName,
        @NotNull Boolean enabled,
        Map<String, Object> settings) {

    public JobSourceSpec {
        settings = settings == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
    }
}
