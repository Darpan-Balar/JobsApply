package com.jobsapply.platform.configimport.model;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.Lists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code kind: JobFamilySet}. */
public record JobFamilySetConfig(@NotEmpty List<@NotNull @Valid JobFamilyConfig> families) {

    public JobFamilySetConfig {
        families = Lists.immutable(families);
    }

    public record JobFamilyConfig(
            @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
            @NotBlank @Size(max = 200) String displayName,
            String description) {}
}
