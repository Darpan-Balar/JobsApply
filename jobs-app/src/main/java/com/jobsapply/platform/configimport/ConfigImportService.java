package com.jobsapply.platform.configimport;

import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.catalog.JobFamilySpec;
import com.jobsapply.platform.catalog.SkillSpec;
import com.jobsapply.platform.configimport.internal.ConfigLoader;
import com.jobsapply.platform.configimport.internal.LoadedConfig;
import com.jobsapply.platform.configimport.internal.SemanticValidator;
import com.jobsapply.platform.configimport.internal.ValidationContext;
import com.jobsapply.platform.configimport.internal.ValidationResult;
import com.jobsapply.platform.configimport.model.EmployerSetConfig;
import com.jobsapply.platform.configimport.model.EmployerSetConfig.EmployerConfig;
import com.jobsapply.platform.configimport.model.JobFamilySetConfig;
import com.jobsapply.platform.configimport.model.JobSourceConfig;
import com.jobsapply.platform.configimport.model.SkillSetConfig;
import com.jobsapply.platform.employer.BindingSpec;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.EmployerSpec;
import com.jobsapply.platform.roleprofile.RoleProfileImportResult;
import com.jobsapply.platform.roleprofile.RoleProfileScope;
import com.jobsapply.platform.roleprofile.RoleProfileService;
import com.jobsapply.platform.source.JobSourceSpec;
import com.jobsapply.platform.source.SourceService;
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
 * Validates and imports configuration — a directory, a single uploaded YAML document, or content converted from CSV.
 * Import is all-or-nothing (one transaction) and only runs when the whole input is valid. A dry run executes the
 * import and rolls it back, so its report shows exactly what would change.
 *
 * <p>Order: job families, skills, job sources, employers (with bindings), role profiles.
 */
@Service
@RequiredArgsConstructor
public class ConfigImportService {

    private final ConfigLoader loader;
    private final SemanticValidator semanticValidator;
    private final CatalogService catalog;
    private final SourceService sources;
    private final EmployerService employers;
    private final RoleProfileService roleProfiles;
    private final TransactionTemplate transactions;

    public ConfigImportReport validate(Path directory) {
        return run(loader.loadDirectory(directory), false, true);
    }

    public ConfigImportReport importDirectory(Path directory, boolean dryRun) {
        return run(loader.loadDirectory(directory), dryRun, false);
    }

    /** Imports one YAML document of any kind (e.g. an uploaded {@code EmployerSet}). */
    public ConfigImportReport importDocument(String fileName, String yaml, boolean dryRun) {
        return run(loader.loadText(fileName, yaml), dryRun, false);
    }

    /**
     * Imports one {@code RoleProfile} document as a private profile of the current user, with the same validation as
     * system configuration. Other kinds are rejected.
     */
    public ConfigImportReport importUserRoleProfile(String fileName, String yaml, boolean dryRun) {
        LoadedConfig config = loader.loadText(fileName, yaml);
        if (config.issues().isEmpty() && config.documents().stream().anyMatch(d -> d.kind() != ConfigKind.ROLE_PROFILE)) {
            return new ConfigImportReport(dryRun, false, List.of(new ConfigIssue(fileName, "kind", "expected kind RoleProfile")),
                    List.of(), List.of());
        }
        if (!config.issues().isEmpty()) {
            return new ConfigImportReport(dryRun, false, config.issues(), List.of(), List.of());
        }
        ValidationResult validation = semanticValidator.validate(config, context());
        if (!validation.issues().isEmpty()) {
            return new ConfigImportReport(dryRun, false, validation.issues(), List.of(), validation.notices());
        }
        List<ConfigItemOutcome> outcomes = transactions.execute(status -> {
            List<ConfigItemOutcome> applied = config.documents().stream().map(doc -> {
                RoleProfileImportResult result = roleProfiles.importDefinition(doc.contentAs(RoleProfileDefinition.class),
                        ConfigKind.ROLE_PROFILE.latestSchemaVersion(), RoleProfileScope.CURRENT_USER);
                return new ConfigItemOutcome(ConfigKind.ROLE_PROFILE, result.key(), result.outcome() + " (v" + result.version().versionNo() + ")");
            }).toList();
            if (dryRun) {
                status.setRollbackOnly();
            }
            return applied;
        });
        return new ConfigImportReport(dryRun, !dryRun, List.of(), outcomes, validation.notices());
    }

    /** Imports already-parsed content (used by the CSV importer). */
    public ConfigImportReport importContent(String fileName, ConfigKind kind, Object content, boolean dryRun) {
        return run(loader.fromContent(fileName, kind, content), dryRun, false);
    }

    private ConfigImportReport run(LoadedConfig config, boolean dryRun, boolean validateOnly) {
        List<ConfigIssue> personal = config.ofKind(ConfigKind.CANDIDATE_PROFILE).stream()
                .map(d -> new ConfigIssue(d.file(), "kind", "candidate profiles are personal data and must not be stored with the "
                        + "platform configuration (it is tracked by git); import them through /api/v1/me/candidate-profiles/import"))
                .toList();
        if (!personal.isEmpty()) {
            return new ConfigImportReport(dryRun, false, personal, List.of(), List.of());
        }
        if (!config.issues().isEmpty()) {
            // Semantic validation only runs on a structurally valid set, to avoid cascades of follow-on errors.
            return new ConfigImportReport(dryRun, false, config.issues(), List.of(), List.of());
        }
        ValidationResult validation = semanticValidator.validate(config, context());
        if (!validation.issues().isEmpty() || validateOnly) {
            return new ConfigImportReport(dryRun, false, validation.issues(), List.of(), validation.notices());
        }
        List<ConfigItemOutcome> outcomes = transactions.execute(status -> {
            List<ConfigItemOutcome> applied = apply(config);
            if (dryRun) {
                status.setRollbackOnly();
            }
            return applied;
        });
        return new ConfigImportReport(dryRun, !dryRun, List.of(), outcomes, validation.notices());
    }

    private ValidationContext context() {
        return new ValidationContext(catalog.snapshot(), sources.idsByKey().keySet(), employers.bindingOwners());
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
                    new SkillSpec(skill.key(), skill.displayName(), skill.category(), new HashSet<>(skill.aliases()), skill.extractAliasesOnly())));
        }
        for (Map.Entry<String, ?> outcome : catalog.upsertSkills(skills).entrySet()) {
            outcomes.add(new ConfigItemOutcome(ConfigKind.SKILL_SET, outcome.getKey(), String.valueOf(outcome.getValue())));
        }

        for (ConfigDocument doc : config.ofKind(ConfigKind.JOB_SOURCE)) {
            JobSourceConfig source = doc.contentAs(JobSourceConfig.class);
            var outcome = sources.upsert(new JobSourceSpec(
                    source.key(), source.adapter(), source.displayName(), source.enabled(), source.settings()));
            outcomes.add(new ConfigItemOutcome(ConfigKind.JOB_SOURCE, source.key(), outcome.name()));
        }

        for (ConfigDocument doc : config.ofKind(ConfigKind.EMPLOYER_SET)) {
            for (EmployerConfig employer : doc.contentAs(EmployerSetConfig.class).employers()) {
                var outcome = employers.upsert(toSpec(employer), toBindings(employer));
                outcomes.add(new ConfigItemOutcome(ConfigKind.EMPLOYER_SET, employer.key(), outcome.name()));
            }
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

    static EmployerSpec toSpec(EmployerConfig employer) {
        return EmployerSpec.builder()
                .key(employer.key())
                .displayName(employer.displayName())
                .website(employer.website())
                .careersUrl(employer.careersUrl())
                .industry(employer.industry())
                .tags(employer.tags())
                .headquarters(employer.headquarters())
                .discoveryPriority(employer.discoveryPriority())
                .enabled(employer.enabled())
                .metadata(employer.metadata())
                .build();
    }

    static List<BindingSpec> toBindings(EmployerConfig employer) {
        return employer.sources().stream()
                .map(s -> new BindingSpec(s.source(), s.externalRef(), s.enabled(), s.priority()))
                .toList();
    }
}
