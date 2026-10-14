package com.jobsapply.platform.configimport.internal;

import static com.jobsapply.platform.configimport.internal.ConfigFixtures.ROLE;
import static com.jobsapply.platform.configimport.internal.ConfigFixtures.issuesOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.configimport.ConfigKind;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Structural validation: parsing, envelope, typed binding and Bean Validation, each with a precise path. */
class ConfigLoaderTest {

    @Test
    void validRoleProfileLoadsAsTypedDefinition() {
        LoadedConfig loaded = ConfigFixtures.load(Map.of("roles/backend.yaml", ROLE));

        assertThat(loaded.issues()).isEmpty();
        RoleProfileDefinition role = loaded.ofKind(ConfigKind.ROLE_PROFILE).getFirst().contentAs(RoleProfileDefinition.class);
        assertThat(role.key()).isEqualTo("backend-engineer");
        assertThat(role.skills().preferred().getFirst().weight()).isEqualTo(2);
        assertThat(role.scoring().weights().get("title")).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void unsupportedSchemaVersionIsRejected() {
        assertThat(issuesOf(ROLE.replace("schemaVersion: 1", "schemaVersion: 2")))
                .extracting(ConfigIssue::toString)
                .containsExactly("test.yaml:schemaVersion: unsupported schemaVersion 2 for RoleProfile; supported: 1..1");
    }

    @Test
    void missingSchemaVersionIsRejected() {
        assertThat(issuesOf(ROLE.replace("schemaVersion: 1\n", "")))
                .extracting(ConfigIssue::path)
                .containsExactly("schemaVersion");
    }

    @Test
    void unknownKindIsRejected() {
        assertThat(issuesOf(ROLE.replace("kind: RoleProfile", "kind: RoleProfil")))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo("kind");
                    assertThat(issue.message()).contains("unknown kind 'RoleProfil'");
                });
    }

    @Test
    void badKeyFormatIsReportedAtItsPath() {
        assertThat(issuesOf(ROLE.replace("key: backend-engineer", "key: Backend_Engineer")))
                .extracting(ConfigIssue::path)
                .containsExactly("key");
    }

    @Test
    void unknownPropertyIsRejectedSoTyposAreNotSilentlyIgnored() {
        assertThat(issuesOf(ROLE.replace("titles:", "titels:")))
                .anySatisfy(issue -> assertThat(issue.message()).contains("titels"));
    }

    @Test
    void wrongTypeIsReportedAtItsPath() {
        assertThat(issuesOf(ROLE.replace("minYears: 1", "minYears: lots")))
                .extracting(ConfigIssue::path)
                .containsExactly("experience.minYears");
    }

    @Test
    void nestedViolationsCarryListIndexesAndMapKeys() {
        String yaml = ROLE.replace("{ skill: kubernetes, weight: 2 }", "{ skill: '', weight: 2 }")
                .replace("title: 0.5", "title: 1.5");
        assertThat(issuesOf(yaml))
                .extracting(ConfigIssue::path)
                .containsExactlyInAnyOrder("scoring.weights[title]", "skills.preferred[0].skill");
    }

    @Test
    void invalidYamlIsReported() {
        assertThat(issuesOf("schemaVersion: 1\nkind: [unclosed"))
                .singleElement()
                .satisfies(issue -> assertThat(issue.message()).startsWith("not valid YAML"));
    }

    @Test
    void duplicateYamlKeysAreRejected() {
        assertThat(issuesOf(ROLE + "key: other\n")).isNotEmpty();
    }

    @Test
    void nonMappingDocumentIsRejected() {
        assertThat(issuesOf("- just\n- a list\n")).extracting(ConfigIssue::message)
                .containsExactly("document must be a YAML mapping");
    }

    @Test
    void problemsInOneFileDoNotHideOtherFiles() {
        LoadedConfig loaded = ConfigFixtures.load(Map.of(
                "a.yaml", ROLE.replace("key: backend-engineer", "key: BAD"),
                "b.yaml", ConfigFixtures.FAMILIES));
        assertThat(loaded.issues()).extracting(ConfigIssue::file).containsExactly("a.yaml");
        assertThat(loaded.documents()).hasSize(1);
        assertThat(List.of(loaded.documents().getFirst().kind())).containsExactly(ConfigKind.JOB_FAMILY_SET);
    }
}
