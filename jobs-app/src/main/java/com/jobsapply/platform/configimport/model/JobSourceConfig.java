package com.jobsapply.platform.configimport.model;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.Lists;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * {@code kind: JobSource} — a configured instance of an adapter plugin. {@code settings} are validated by the plugin
 * (Phase 2); they may contain secret references ({@code env:NAME}) but never secret values.
 */
public record JobSourceConfig(
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
        @NotBlank @Pattern(regexp = Keys.PLUGIN_ID_REGEX) String adapter,
        @NotBlank @Size(max = 200) String displayName,
        Boolean enabled,
        Map<String, Object> settings) {

    public JobSourceConfig {
        enabled = enabled == null || enabled;
        settings = Lists.immutable(settings);
    }
}
