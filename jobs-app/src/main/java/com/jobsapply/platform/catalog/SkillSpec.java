package com.jobsapply.platform.catalog;

import java.util.Set;
import lombok.Builder;

/** @param aliases synonyms (e.g. {@code c++} for {@code cpp}); normalized on import */
@Builder
public record SkillSpec(String key, String displayName, String category, Set<String> aliases, boolean extractAliasesOnly) {

    public SkillSpec {
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
    }

    public SkillSpec(String key, String displayName, String category, Set<String> aliases) {
        this(key, displayName, category, aliases, false);
    }
}
