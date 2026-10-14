package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.catalog.CatalogSnapshot;
import com.jobsapply.platform.catalog.SkillNames;
import com.jobsapply.platform.configimport.ConfigDocument;
import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.configimport.ConfigKind;
import com.jobsapply.platform.configimport.model.EmployerSetConfig;
import com.jobsapply.platform.configimport.model.EmployerSetConfig.EmployerConfig;
import com.jobsapply.platform.configimport.model.EmployerSetConfig.EmployerSourceConfig;
import com.jobsapply.platform.configimport.model.JobFamilySetConfig;
import com.jobsapply.platform.configimport.model.JobSourceConfig;
import com.jobsapply.platform.configimport.model.SkillSetConfig;
import com.jobsapply.platform.configimport.model.SkillSetConfig.SkillConfig;
import com.jobsapply.platform.matching.MatchingVocabulary;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import com.jobsapply.spi.profile.RoleProfileDefinition.ScreeningPolicy;
import com.jobsapply.spi.profile.RoleProfileDefinition.ScreeningQuestionRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cross-field and cross-document rules for a whole configuration set, evaluated against the union of the set and
 * what is already in the database: unique keys, resolvable references, weights and thresholds, regexes, and the ban on
 * literal secrets.
 */
@Component
@RequiredArgsConstructor
public class SemanticValidator {

    private static final BigDecimal WEIGHT_SUM = BigDecimal.ONE;
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.0001");
    private static final Pattern SECRET_LIKE_KEY =
            Pattern.compile("(?i).*(password|passwd|secret|token|api[-_]?key|credential|private[-_]?key).*");

    private final MatchingVocabulary vocabulary;

    public List<ConfigIssue> validate(LoadedConfig config, CatalogSnapshot database) {
        List<ConfigIssue> issues = new ArrayList<>();
        Set<String> families = new HashSet<>(database.jobFamilyKeys());
        families.addAll(validateFamilies(config, issues));
        SkillIndex skills = validateSkills(config, database, issues);
        Set<String> sources = validateSources(config, issues);
        validateRoleProfiles(config, families, skills, issues);
        validateEmployers(config, sources, issues);
        return issues;
    }

    private Set<String> validateFamilies(LoadedConfig config, List<ConfigIssue> issues) {
        Map<String, String> seen = new HashMap<>();
        for (ConfigDocument doc : config.ofKind(ConfigKind.JOB_FAMILY_SET)) {
            List<JobFamilySetConfig.JobFamilyConfig> list = doc.contentAs(JobFamilySetConfig.class).families();
            for (int i = 0; i < list.size(); i++) {
                checkUnique(seen, list.get(i).key(), doc.file(), "families[" + i + "].key", "job family", issues);
            }
        }
        return seen.keySet();
    }

    private record SkillIndex(Set<String> keys, Map<String, String> aliasToSkill) {}

    private SkillIndex validateSkills(LoadedConfig config, CatalogSnapshot database, List<ConfigIssue> issues) {
        Map<String, String> seenKeys = new HashMap<>();
        List<ConfigDocument> docs = config.ofKind(ConfigKind.SKILL_SET);
        for (ConfigDocument doc : docs) {
            List<SkillConfig> list = doc.contentAs(SkillSetConfig.class).skills();
            for (int i = 0; i < list.size(); i++) {
                checkUnique(seenKeys, list.get(i).key(), doc.file(), "skills[" + i + "].key", "skill", issues);
            }
        }
        Set<String> keys = new HashSet<>(database.skillKeys());
        keys.addAll(seenKeys.keySet());

        // Aliases of skills not redefined here stay as they are in the database.
        Map<String, String> aliasToSkill = new HashMap<>();
        database.aliasToSkill().forEach((alias, skill) -> {
            if (!seenKeys.containsKey(skill)) {
                aliasToSkill.put(alias, skill);
            }
        });
        for (ConfigDocument doc : docs) {
            List<SkillConfig> list = doc.contentAs(SkillSetConfig.class).skills();
            for (int i = 0; i < list.size(); i++) {
                SkillConfig skill = list.get(i);
                for (int j = 0; j < skill.aliases().size(); j++) {
                    String alias = SkillNames.normalize(skill.aliases().get(j));
                    String path = "skills[" + i + "].aliases[" + j + "]";
                    if (keys.contains(alias)) {
                        issues.add(new ConfigIssue(doc.file(), path, "alias '" + alias + "' equals the skill key '" + alias + "'"));
                        continue;
                    }
                    String owner = aliasToSkill.putIfAbsent(alias, skill.key());
                    if (owner != null && !owner.equals(skill.key())) {
                        issues.add(new ConfigIssue(doc.file(), path, "alias '" + alias + "' is already used by skill '" + owner + "'"));
                    }
                }
            }
        }
        return new SkillIndex(keys, aliasToSkill);
    }

    private Set<String> validateSources(LoadedConfig config, List<ConfigIssue> issues) {
        Map<String, String> seen = new HashMap<>();
        for (ConfigDocument doc : config.ofKind(ConfigKind.JOB_SOURCE)) {
            JobSourceConfig source = doc.contentAs(JobSourceConfig.class);
            checkUnique(seen, source.key(), doc.file(), "key", "job source", issues);
            findLiteralSecrets(source.settings(), "settings", doc.file(), issues);
        }
        return seen.keySet();
    }

    private void validateRoleProfiles(LoadedConfig config, Set<String> families, SkillIndex skills, List<ConfigIssue> issues) {
        Map<String, String> seen = new HashMap<>();
        for (ConfigDocument doc : config.ofKind(ConfigKind.ROLE_PROFILE)) {
            String file = doc.file();
            RoleProfileDefinition role = doc.contentAs(RoleProfileDefinition.class);
            checkUnique(seen, role.key(), file, "key", "role profile", issues);

            if (!families.contains(role.jobFamily())) {
                issues.add(new ConfigIssue(file, "jobFamily", "unknown job family '" + role.jobFamily() + "'"));
            }

            Set<String> include = lower(role.titles().include());
            for (int i = 0; i < role.titles().exclude().size(); i++) {
                if (include.contains(role.titles().exclude().get(i).toLowerCase(Locale.ROOT))) {
                    issues.add(new ConfigIssue(file, "titles.exclude[" + i + "]", "phrase is also listed in titles.include"));
                }
            }

            for (int i = 0; i < role.skills().required().size(); i++) {
                checkSkill(role.skills().required().get(i), skills, file, "skills.required[" + i + "]", issues);
            }
            for (int i = 0; i < role.skills().requiredAnyOf().size(); i++) {
                List<String> group = role.skills().requiredAnyOf().get(i);
                for (int j = 0; j < group.size(); j++) {
                    checkSkill(group.get(j), skills, file, "skills.requiredAnyOf[" + i + "][" + j + "]", issues);
                }
            }
            for (int i = 0; i < role.skills().preferred().size(); i++) {
                checkSkill(role.skills().preferred().get(i).skill(), skills, file, "skills.preferred[" + i + "].skill", issues);
            }

            if (role.experience() != null && role.experience().minYears() != null && role.experience().maxYears() != null
                    && role.experience().minYears() > role.experience().maxYears()) {
                issues.add(new ConfigIssue(file, "experience", "minYears must not exceed maxYears"));
            }

            for (int i = 0; i < role.eligibility().size(); i++) {
                String type = role.eligibility().get(i).type();
                if (!vocabulary.eligibilityRuleTypes().contains(type)) {
                    issues.add(new ConfigIssue(file, "eligibility[" + i + "].type", "unknown eligibility rule type '" + type
                            + "'; known: " + String.join(", ", new TreeSet<>(vocabulary.eligibilityRuleTypes()))));
                }
            }

            validateScoring(role, file, issues);
            validateScreening(role.screeningQuestions(), file, issues);
        }
    }

    private void validateScoring(RoleProfileDefinition role, String file, List<ConfigIssue> issues) {
        var scoring = role.scoring();
        if (!vocabulary.strategies().contains(scoring.strategy())) {
            issues.add(new ConfigIssue(file, "scoring.strategy", "unknown scoring strategy '" + scoring.strategy() + "'"));
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> weight : scoring.weights().entrySet()) {
            if (!vocabulary.scoreComponents().contains(weight.getKey())) {
                issues.add(new ConfigIssue(file, "scoring.weights[" + weight.getKey() + "]", "unknown score component '"
                        + weight.getKey() + "'; known: " + String.join(", ", new TreeSet<>(vocabulary.scoreComponents()))));
            }
            if (weight.getValue() != null) {
                sum = sum.add(weight.getValue());
            }
        }
        if (sum.subtract(WEIGHT_SUM).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
            issues.add(new ConfigIssue(file, "scoring.weights", "weights must sum to 1 (found " + sum.stripTrailingZeros().toPlainString() + ")"));
        }
        var thresholds = scoring.thresholds();
        if (thresholds.minimum().compareTo(thresholds.shortlist()) > 0) {
            issues.add(new ConfigIssue(file, "scoring.thresholds", "minimum must not exceed shortlist"));
        }
    }

    private static void validateScreening(List<ScreeningQuestionRule> rules, String file, List<ConfigIssue> issues) {
        for (int i = 0; i < rules.size(); i++) {
            ScreeningQuestionRule rule = rules.get(i);
            try {
                Pattern.compile(rule.match());
            } catch (PatternSyntaxException e) {
                issues.add(new ConfigIssue(file, "screeningQuestions[" + i + "].match", "invalid regular expression: " + e.getDescription()));
            }
            if (rule.policy() == ScreeningPolicy.FROM_FACT && (rule.fact() == null || rule.fact().isBlank())) {
                issues.add(new ConfigIssue(file, "screeningQuestions[" + i + "].fact", "is required when policy is FROM_FACT"));
            }
        }
    }

    private void validateEmployers(LoadedConfig config, Set<String> sources, List<ConfigIssue> issues) {
        Map<String, String> seenKeys = new HashMap<>();
        Map<String, String> seenRefs = new HashMap<>();
        for (ConfigDocument doc : config.ofKind(ConfigKind.EMPLOYER_SET)) {
            List<EmployerConfig> employers = doc.contentAs(EmployerSetConfig.class).employers();
            for (int i = 0; i < employers.size(); i++) {
                EmployerConfig employer = employers.get(i);
                checkUnique(seenKeys, employer.key(), doc.file(), "employers[" + i + "].key", "employer", issues);
                for (int j = 0; j < employer.sources().size(); j++) {
                    EmployerSourceConfig binding = employer.sources().get(j);
                    String path = "employers[" + i + "].sources[" + j + "]";
                    if (!sources.contains(binding.source())) {
                        issues.add(new ConfigIssue(doc.file(), path + ".source", "unknown job source '" + binding.source() + "'"));
                    }
                    String ref = binding.source() + "/" + binding.externalRef();
                    String owner = seenRefs.putIfAbsent(ref, employer.key());
                    if (owner != null) {
                        issues.add(new ConfigIssue(doc.file(), path + ".externalRef",
                                "'" + binding.externalRef() + "' on " + binding.source() + " is already bound to employer '" + owner + "'"));
                    }
                }
            }
        }
    }

    private static void checkSkill(String skill, SkillIndex skills, String file, String path, List<ConfigIssue> issues) {
        if (skills.keys().contains(skill)) {
            return;
        }
        String canonical = skills.aliasToSkill().get(SkillNames.normalize(skill));
        issues.add(new ConfigIssue(file, path, canonical != null
                ? "'" + skill + "' is an alias; use the canonical skill key '" + canonical + "'"
                : "unknown skill '" + skill + "'"));
    }

    private static void checkUnique(
            Map<String, String> seen, String key, String file, String path, String what, List<ConfigIssue> issues) {
        String firstFile = seen.putIfAbsent(key, file);
        if (firstFile != null) {
            issues.add(new ConfigIssue(file, path, "duplicate " + what + " key '" + key + "' (also defined in " + firstFile + ")"));
        }
    }

    private static void findLiteralSecrets(Object value, String path, String file, List<ConfigIssue> issues) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                String childPath = path + "." + k;
                if (SECRET_LIKE_KEY.matcher(String.valueOf(k)).matches() && v instanceof String s
                        && !(s.startsWith("env:") || s.startsWith("secret:"))) {
                    issues.add(new ConfigIssue(file, childPath,
                            "looks like a secret; configuration may only hold a reference such as env:VARIABLE_NAME"));
                } else {
                    findLiteralSecrets(v, childPath, file, issues);
                }
            });
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                findLiteralSecrets(list.get(i), path + "[" + i + "]", file, issues);
            }
        }
    }

    private static Set<String> lower(List<String> values) {
        Set<String> result = new HashSet<>();
        values.forEach(v -> result.add(v.toLowerCase(Locale.ROOT)));
        return result;
    }
}
