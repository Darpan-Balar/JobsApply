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

/** {@code kind: SkillSet}. Aliases are synonyms resolved to the canonical {@code key}. */
public record SkillSetConfig(@NotEmpty List<@NotNull @Valid SkillConfig> skills) {

    public SkillSetConfig {
        skills = Lists.immutable(skills);
    }

    public record SkillConfig(
            @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
            @NotBlank @Size(max = 200) String displayName,
            @Pattern(regexp = Keys.KEY_REGEX) String category,
            List<@NotBlank @Size(max = 100) String> aliases,
            /* Find this skill in job text only by its aliases (key/name is an ordinary word, e.g. "go"). */
            Boolean extractAliasesOnly) {
        public SkillConfig {
            aliases = Lists.immutable(aliases);
            extractAliasesOnly = Boolean.TRUE.equals(extractAliasesOnly);
        }
    }
}
