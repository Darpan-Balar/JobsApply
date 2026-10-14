package com.jobsapply.platform.configimport;

import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.catalog.JobFamilySpec;
import com.jobsapply.platform.catalog.SkillSpec;
import com.jobsapply.platform.configimport.internal.ConfigLoader;
import com.jobsapply.platform.configimport.internal.LoadedConfig;
import com.jobsapply.platform.configimport.internal.SemanticValidator;
import com.jobsapply.platform.configimport.model.JobFamilySetConfig;
import com.jobsapply.platform.configimport.model.SkillSetConfig;
import com.jobsapply.platform.roleprofile.RoleProfileImportResult;
import com.jobsapply.platform.roleprofile.RoleProfileScope;
import com.jobsapply.platform.roleprofile.RoleProfileService;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Validates and imports a configuration directory. Import is all-or-nothing (one transaction) and only runs on a fully
 * valid configuration. A dry run executes the import and rolls it back, so its report shows exactly what would change.
 */
@Service
@RequiredArgsConstructor
public class ConfigImportService {

    private final ConfigLoader loader;
    private final SemanticValidator semanticValidator;
    private final CatalogService catalog;
    private final RoleProfileService roleProfiles;
    private final TransactionTemplate transactions;

    public ConfigImportReport validate(Path directory) {
        LoadedConfig config = loader.loadDirectory(directory);
        List<ConfigIssue> issues = issuesOf(config);
        return new ConfigImportReport(false, false, issues, List.of(), notices(config));
    }

    public ConfigImportReport importDirectory(Path directory, boolean dryRun) {
        LoadedConfig config = loader.loadDirectory(directory);
        List<ConfigIssue> issues = issuesOf(config);
        if (!issues.isEmpty()) {
            return new ConfigImportReport(dryRun, false, issues, List.of(), notices(config));
        }
        List<ConfigItemOutcome> outcomes = transactions.execute(status -> {
            List<ConfigItemOutcome> applied = apply(config);
            if (dryRun) {
                status.setRollbackOnly();
            }
            return applied;
        });
        return new ConfigImportReport(dryRun, !dryRun, List.of(), outcomes, notices(config));
    }

    /** Structural problems first; semantic validation only runs on a structurally valid set (avoids cascades). */
    private List<ConfigIssue> issuesOf(LoadedConfig config) {
        if (!config.issues().isEmpty()) {
            return config.issues();
        }
        return semanticValidator.validate(config, catalog.snapshot());
    }

    private List<ConfigItemOutcome> apply(LoadedConfig config) {
        List<ConfigItemOutcome> outcomes = new ArrayList<>();
        for (ConfigDocument doc : config.ofKind(ConfigKind.JOB_FAMILY_SET)) {
            for (JobFamilySetConfig.JobFamilyConfig family : doc.contentAs(JobFamilySetConfig.class).families()) {
                var outcome = catalog.upsertJobFamily(new JobFamilySpec(family.key(), family.displayName(), family.description()));
                outcomes.add(new ConfigItemOutcome(ConfigKind.JOB_FAMILY_SET, family.key(), outcome.name()));
            }
        }

        List<SkillSpec> skills = new ArrayList<>();
        for (ConfigDocument doc : config.ofKind(ConfigKind.SKILL_SET)) {
            doc.contentAs(SkillSetConfig.class).skills().forEach(skill -> skills.add(
                    new SkillSpec(skill.key(), skill.displayName(), skill.category(), new HashSet<>(skill.aliases()))));
        }
        for (Map.Entry<String, ?> outcome : catalog.upsertSkills(skills).entrySet()) {
            outcomes.add(new ConfigItemOutcome(ConfigKind.SKILL_SET, outcome.getKey(), String.valueOf(outcome.getValue())));
        }

        for (ConfigDocument doc : config.ofKind(ConfigKind.ROLE_PROFILE)) {
            RoleProfileImportResult result = roleProfiles.importDefinition(
                    doc.contentAs(RoleProfileDefinition.class), ConfigKind.ROLE_PROFILE.latestSchemaVersion(),
                    RoleProfileScope.SYSTEM_TEMPLATE);
            outcomes.add(new ConfigItemOutcome(ConfigKind.ROLE_PROFILE, result.key(),
                    result.outcome() + " (v" + result.version().versionNo() + ")"));
        }
        return outcomes;
    }

    private static List<String> notices(LoadedConfig config) {
        List<String> notices = new ArrayList<>();
        int deferred = config.ofKind(ConfigKind.JOB_SOURCE).size() + config.ofKind(ConfigKind.EMPLOYER_SET).size();
        if (deferred > 0) {
            notices.add(deferred + " JobSource/EmployerSet document(s) were validated; importing them arrives in Phase 2");
        }
        return notices;
    }
}
