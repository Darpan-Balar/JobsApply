package com.jobsapply.platform.employer;

import com.jobsapply.spi.common.Keys;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.Builder;

/**
 * Desired state of an employer's details (bindings are separate).
 *
 * @param metadata free-form attributes; {@code null} means "leave unchanged" (e.g. CSV imports cannot express it)
 */
@Builder(toBuilder = true)
public record EmployerSpec(
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 2048) @Pattern(regexp = HTTP_URL) String website,
        @Size(max = 2048) @Pattern(regexp = HTTP_URL) String careersUrl,
        @Size(max = 100) String industry,
        List<@NotBlank @Pattern(regexp = Keys.KEY_REGEX) String> tags,
        @Size(max = 200) String headquarters,
        @NotNull @Min(0) @Max(100) Integer discoveryPriority,
        @NotNull Boolean enabled,
        Map<String, Object> metadata) {

    public static final String HTTP_URL = "^https?://\\S+$";

    public EmployerSpec {
        // Tags are a set: sorted and de-duplicated so equal content compares equal.
        tags = tags == null ? List.of() : List.copyOf(new TreeSet<>(tags.stream().filter(t -> t != null).toList()));
        metadata = metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
