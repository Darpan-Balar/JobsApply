package com.jobsapply.platform.configimport.internal;

import static com.jobsapply.platform.configimport.internal.ConfigFixtures.EMPLOYERS;
import static com.jobsapply.platform.configimport.internal.ConfigFixtures.FAMILIES;
import static com.jobsapply.platform.configimport.internal.ConfigFixtures.ROLE;
import static com.jobsapply.platform.configimport.internal.ConfigFixtures.SKILLS;
import static com.jobsapply.platform.configimport.internal.ConfigFixtures.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.catalog.CatalogSnapshot;
import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.matching.MatchingVocabulary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cross-field and cross-document rules. */
class SemanticValidatorTest {

    private static final MatchingVocabulary VOCABULARY = new MatchingVocabulary() {
        public Set<String> eligibilityRuleTypes() {
            return Set.of("title-match", "work-authorization");
        }

        public Set<String> scoreComponents() {
            return Set.of("title", "required-skills", "preferred-skills");
        }

        public Set<String> strategies() {
            return Set.of("weighted");
        }
    };

    private static final CatalogSnapshot EMPTY_DATABASE = new CatalogSnapshot(Set.of(), Set.of(), Map.of());

    private final SemanticValidator validator = new SemanticValidator(VOCABULARY);

    private List<ConfigIssue> validate(Map<String, String> overrides) {
        return validate(overrides, EMPTY_DATABASE);
    }

    private List<ConfigIssue> validate(Map<String, String> overrides, CatalogSnapshot database) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("families.yaml", FAMILIES);
        files.put("skills.yaml", SKILLS);
        files.put("role.yaml", ROLE);
        files.put("source.yaml", SOURCE);
        files.put("employers.yaml", EMPLOYERS);
        files.putAll(overrides);
        LoadedConfig loaded = ConfigFixtures.load(files);
        assertThat(loaded.issues()).as("fixtures must be structurally valid").isEmpty();
        return validator.validate(loaded, database);
    }

    private static List<String> rendered(List<ConfigIssue> issues) {
        return issues.stream().map(ConfigIssue::toString).toList();
    }

    @Test
    void consistentConfigurationIsValid() {
        assertThat(validate(Map.of())).isEmpty();
    }

    @Test
    void referencesMayResolveAgainstTheDatabaseCatalog() {
        CatalogSnapshot database = new CatalogSnapshot(Set.of("backend"), Set.of("java", "sql", "kubernetes"), Map.of());
        assertThat(validate(Map.of("families.yaml", FAMILIES.replace("key: backend", "key: other-family")), database)).isEmpty();
    }

    @Test
    void unknownSkillIsRejected() {
        assertThat(rendered(validate(Map.of("role.yaml", ROLE.replace("required: [java]", "required: [jaava]")))))
                .containsExactly("role.yaml:skills.required[0]: unknown skill 'jaava'");
    }

    @Test
    void aliasInsteadOfCanonicalKeySuggestsTheKey() {
        assertThat(rendered(validate(Map.of("role.yaml", ROLE.replace("skill: kubernetes", "skill: k8s")))))
                .containsExactly("role.yaml:skills.preferred[0].skill: 'k8s' is an alias; use the canonical skill key 'kubernetes'");
    }

    @Test
    void weightsMustSumToOne() {
        assertThat(rendered(validate(Map.of("role.yaml", ROLE.replace("required-skills: 0.5", "required-skills: 0.4")))))
                .containsExactly("role.yaml:scoring.weights: weights must sum to 1 (found 0.9)");
    }

    @Test
    void thresholdsMustBeOrdered() {
        assertThat(rendered(validate(Map.of("role.yaml", ROLE.replace("minimum: 50", "minimum: 80")))))
                .containsExactly("role.yaml:scoring.thresholds: minimum must not exceed shortlist");
    }

    @Test
    void unknownRuleTypeAndScoreComponentAreRejected() {
        String yaml = ROLE.replace("type: title-match", "type: title-matches")
                .replace("required-skills: 0.5", "required-skill: 0.5");
        assertThat(validate(Map.of("role.yaml", yaml)))
                .extracting(ConfigIssue::path)
                .containsExactlyInAnyOrder("eligibility[0].type", "scoring.weights[required-skill]");
    }

    @Test
    void unknownJobFamilyIsRejected() {
        assertThat(rendered(validate(Map.of("role.yaml", ROLE.replace("jobFamily: backend", "jobFamily: backnd")))))
                .containsExactly("role.yaml:jobFamily: unknown job family 'backnd'");
    }

    @Test
    void experienceRangeMustBeOrdered() {
        assertThat(validate(Map.of("role.yaml", ROLE.replace("minYears: 1", "minYears: 9"))))
                .extracting(ConfigIssue::path)
                .containsExactly("experience");
    }

    @Test
    void duplicateKeysAcrossFilesAreRejected() {
        assertThat(rendered(validate(Map.of("role-copy.yaml", ROLE))))
                .containsExactly("role-copy.yaml:key: duplicate role profile key 'backend-engineer' (also defined in role.yaml)");
    }

    @Test
    void invalidRegexAndMissingFactAreRejected() {
        String yaml = ROLE.replace("{ match: \"(?i)visa\", policy: ALWAYS_ASK_USER }", "{ match: \"([\", policy: FROM_FACT }");
        assertThat(validate(Map.of("role.yaml", yaml)))
                .extracting(ConfigIssue::path)
                .containsExactlyInAnyOrder("screeningQuestions[0].match", "screeningQuestions[0].fact");
    }

    @Test
    void literalSecretsInSourceSettingsAreRejectedButReferencesAllowed() {
        String literal = SOURCE + "  apiKey: abc123\n";
        String reference = SOURCE + "  apiKey: env:GREENHOUSE_KEY\n";
        assertThat(rendered(validate(Map.of("source.yaml", literal))))
                .containsExactly("source.yaml:settings.apiKey: looks like a secret; configuration may only hold a reference such as env:VARIABLE_NAME");
        assertThat(validate(Map.of("source.yaml", reference))).isEmpty();
    }

    @Test
    void employerBindingsMustReferenceKnownSourcesAndBeUnique() {
        String employers = EMPLOYERS + """
                  - key: other-co
                    displayName: Other Co
                    sources:
                      - { source: greenhouse-public, externalRef: exampleco }
                      - { source: workday, externalRef: otherco }
                """;
        assertThat(rendered(validate(Map.of("employers.yaml", employers)))).containsExactlyInAnyOrder(
                "employers.yaml:employers[1].sources[0].externalRef: 'exampleco' on greenhouse-public is already bound to employer 'example-co'",
                "employers.yaml:employers[1].sources[1].source: unknown job source 'workday'");
    }

    @Test
    void aliasMayNotBelongToTwoSkillsOrShadowAKey() {
        String skills = SKILLS + "  - { key: k-eight-s, displayName: Other, aliases: [K8S, java] }\n";
        assertThat(rendered(validate(Map.of("skills.yaml", skills)))).containsExactlyInAnyOrder(
                "skills.yaml:skills[3].aliases[0]: alias 'k8s' is already used by skill 'kubernetes'",
                "skills.yaml:skills[3].aliases[1]: alias 'java' equals the skill key 'java'");
    }
}
