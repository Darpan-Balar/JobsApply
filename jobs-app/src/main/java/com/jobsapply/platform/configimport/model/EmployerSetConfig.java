package com.jobsapply.platform.configimport.model;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.Lists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** {@code kind: EmployerSet}. */
public record EmployerSetConfig(@NotEmpty List<@NotNull @Valid EmployerConfig> employers) {

    public EmployerSetConfig {
        employers = Lists.immutable(employers);
    }

    public record EmployerConfig(
            @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
            @NotBlank @Size(max = 200) String displayName,
            @Pattern(regexp = HTTP_URL) String website,
            @Pattern(regexp = HTTP_URL) String careersUrl,
            @Size(max = 100) String industry,
            List<@NotBlank @Pattern(regexp = Keys.KEY_REGEX) String> tags,
            @Size(max = 200) String headquarters,
            @Min(0) @Max(100) Integer discoveryPriority,
            Boolean enabled,
            /* Free-form attributes. Absent = keep what is stored; {} clears. */
            Map<String, Object> metadata,
            List<@NotNull @Valid EmployerSourceConfig> sources) {
        public EmployerConfig {
            tags = Lists.immutable(tags);
            sources = Lists.immutable(sources);
            discoveryPriority = discoveryPriority == null ? 50 : discoveryPriority;
            enabled = enabled == null || enabled;
            metadata = metadata == null ? null : Lists.immutable(metadata);
        }
    }

    /**
     * @param source key of a configured {@code JobSource}
     * @param externalRef the provider's identifier for this employer (board token, site name, ...)
     */
    public record EmployerSourceConfig(
            @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String source,
            @NotBlank @Size(max = 200) String externalRef,
            Boolean enabled,
            @Min(0) @Max(100) Integer priority) {
        public EmployerSourceConfig {
            enabled = enabled == null || enabled;
            priority = priority == null ? 50 : priority;
        }
    }

    public static final String HTTP_URL = "^https?://\\S+$";
}
