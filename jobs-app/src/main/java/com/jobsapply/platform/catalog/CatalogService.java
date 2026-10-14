package com.jobsapply.platform.catalog;

import com.jobsapply.platform.catalog.internal.JobFamily;
import com.jobsapply.platform.catalog.internal.JobFamilyRepository;
import com.jobsapply.platform.catalog.internal.Skill;
import com.jobsapply.platform.catalog.internal.SkillRepository;
import com.jobsapply.platform.shared.model.UpsertOutcome;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the catalog module. Writes are idempotent upserts keyed by the business key. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final JobFamilyRepository jobFamilies;
    private final SkillRepository skills;

    @Transactional
    public UpsertOutcome upsertJobFamily(JobFamilySpec spec) {
        Optional<JobFamily> existing = jobFamilies.findByKey(spec.key());
        if (existing.isEmpty()) {
            jobFamilies.save(JobFamily.builder()
                    .key(spec.key())
                    .displayName(spec.displayName())
                    .description(spec.description())
                    .build());
            return UpsertOutcome.CREATED;
        }
        return existing.get().update(spec.displayName(), spec.description())
                ? UpsertOutcome.UPDATED
                : UpsertOutcome.UNCHANGED;
    }

    /**
     * Upserts a set of skills. Aliases may move between skills; removals are flushed before additions so the global
     * alias uniqueness constraint is never transiently violated.
     */
    @Transactional
    public Map<String, UpsertOutcome> upsertSkills(List<SkillSpec> specs) {
        Map<String, Skill> byKey = new HashMap<>();
        Map<String, Set<String>> normalizedAliases = new HashMap<>();
        Map<String, Boolean> changed = new HashMap<>();
        for (SkillSpec spec : specs) {
            Set<String> aliases = spec.aliases().stream().map(SkillNames::normalize).collect(Collectors.toSet());
            normalizedAliases.put(spec.key(), aliases);
            skills.findByKey(spec.key()).ifPresent(skill -> {
                byKey.put(spec.key(), skill);
                changed.put(spec.key(), skill.retainAliases(aliases));
            });
        }
        skills.flush();

        Map<String, UpsertOutcome> outcomes = new LinkedHashMap<>();
        for (SkillSpec spec : specs) {
            Skill skill = byKey.get(spec.key());
            if (skill == null) {
                Skill created = Skill.builder()
                        .key(spec.key())
                        .displayName(spec.displayName())
                        .category(spec.category())
                        .build();
                created.addAliases(normalizedAliases.get(spec.key()));
                skills.save(created);
                outcomes.put(spec.key(), UpsertOutcome.CREATED);
            } else {
                boolean detailsChanged = skill.update(spec.displayName(), spec.category());
                boolean aliasesAdded = skill.addAliases(normalizedAliases.get(spec.key()));
                boolean anyChange = detailsChanged || aliasesAdded || changed.get(spec.key());
                outcomes.put(spec.key(), anyChange ? UpsertOutcome.UPDATED : UpsertOutcome.UNCHANGED);
            }
        }
        return outcomes;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findJobFamilyId(String key) {
        return jobFamilies.findByKey(key).map(JobFamily::getId);
    }

    @Transactional(readOnly = true)
    public CatalogSnapshot snapshot() {
        Set<String> familyKeys = jobFamilies.findAll().stream().map(JobFamily::getKey).collect(Collectors.toSet());
        Map<String, String> aliasToSkill = new HashMap<>();
        Set<String> skillKeys = new HashSet<>();
        for (Skill skill : skills.findAllWithAliases()) {
            skillKeys.add(skill.getKey());
            skill.aliasNames().forEach(alias -> aliasToSkill.put(alias, skill.getKey()));
        }
        return new CatalogSnapshot(familyKeys, skillKeys, aliasToSkill);
    }
}
