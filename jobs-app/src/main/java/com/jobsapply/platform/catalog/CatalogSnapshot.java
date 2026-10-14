package com.jobsapply.platform.catalog;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Read-only view of the catalog used for validation and normalization. */
public record CatalogSnapshot(Set<String> jobFamilyKeys, Set<String> skillKeys, Map<String, String> aliasToSkill) {

    public CatalogSnapshot {
        jobFamilyKeys = Set.copyOf(jobFamilyKeys);
        skillKeys = Set.copyOf(skillKeys);
        aliasToSkill = Map.copyOf(aliasToSkill);
    }

    /** Resolves a canonical key or an alias to the canonical skill key. */
    public Optional<String> resolveSkill(String keyOrAlias) {
        String normalized = SkillNames.normalize(keyOrAlias);
        if (skillKeys.contains(normalized)) {
            return Optional.of(normalized);
        }
        return Optional.ofNullable(aliasToSkill.get(normalized));
    }
}
