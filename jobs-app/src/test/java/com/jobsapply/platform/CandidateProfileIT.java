package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.candidate.CandidateProfileDetail;
import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.catalog.SkillSpec;
import com.jobsapply.platform.configimport.CandidateImportService;
import com.jobsapply.platform.configimport.ConfigImportReport;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.spi.common.VerificationStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CandidateProfileIT {

    @Autowired CandidateImportService imports;
    @Autowired CandidateProfileService candidates;
    @Autowired ConfigImportService configImports;
    @Autowired CatalogService catalog;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path temp;

    UUID user;
    String key;

    @BeforeEach
    void setUp() {
        catalog.upsertSkills(List.of(new SkillSpec("java", "Java", null, Set.of()), new SkillSpec("postgresql", "PostgreSQL", null, Set.of("postgres")),
                new SkillSpec("spring-boot", "Spring Boot", null, Set.of()), new SkillSpec("kafka", "Kafka", null, Set.of())));
        user = users.createUser(NewUser.builder().username(uniqueKey("cand")).displayName("C").build()).id();
        key = uniqueKey("main");
    }

    private ConfigImportReport importAs(String yaml, boolean trust) {
        return UserContext.callAs(user, () -> imports.importProfile("me.yaml", yaml, trust, false));
    }

    private CandidateProfileDetail detail() {
        return UserContext.callAs(user, () -> candidates.detail(key));
    }

    @Test
    void importedFactsAreUnverifiedUntilConfirmed() {
        ConfigImportReport report = importAs(CandidateYaml.profile(key), false);

        assertThat(report.issues()).isEmpty();
        assertThat(report.outcomes()).singleElement().satisfies(o -> assertThat(o.outcome()).isEqualTo("CREATED"));
        assertThat(report.notices()).singleElement().asString().contains("UNVERIFIED");
        assertThat(detail().factsByVerification())
                .containsEntry(VerificationStatus.UNVERIFIED, 5L)
                .containsEntry(VerificationStatus.USER_PREFERENCE, 1L);

        UserContext.runAs(user, () -> candidates.verifyFacts(key, Set.of("exp-acme", "sk-java"), false));
        assertThat(detail().factsByVerification()).containsEntry(VerificationStatus.VERIFIED, 2L);
        assertThat(detail().currentVersion().versionNo()).isEqualTo(2);

        UserContext.runAs(user, () -> candidates.verifyFacts(key, Set.of(), true));
        assertThat(detail().factsByVerification()).containsEntry(VerificationStatus.VERIFIED, 5L).doesNotContainKey(VerificationStatus.UNVERIFIED);
    }

    @Test
    void trustedImportKeepsDeclaredStatusesAndReimportIsIdempotent() {
        importAs(CandidateYaml.profile(key), true);
        assertThat(detail().factsByVerification()).containsEntry(VerificationStatus.VERIFIED, 5L);

        assertThat(importAs(CandidateYaml.profile(key), true).outcomes().getFirst().outcome()).isEqualTo("UNCHANGED");
        assertThat(importAs(CandidateYaml.profile(key).replace("Senior Backend Engineer", "Staff Engineer"), true)
                .outcomes().getFirst().outcome()).isEqualTo("NEW_VERSION");
    }

    @Test
    void verifyingUnknownFactsFails() {
        importAs(CandidateYaml.profile(key), false);
        assertThatThrownBy(() -> UserContext.runAs(user, () -> candidates.verifyFacts(key, Set.of("nope"), false)))
                .isInstanceOf(DomainRuleViolationException.class).hasMessage("Unknown fact id(s): nope");
    }

    @Test
    void skillsMustBeCanonicalCatalogKeys() {
        String yaml = CandidateYaml.profile(key).replace("skills: [java, postgresql]", "skills: [java, postgres]")
                .replace("skill: java, years: 7", "skill: cobol, years: 7");
        assertThat(importAs(yaml, false).issues()).extracting(ConfigIssue::toString).containsExactlyInAnyOrder(
                "me.yaml:experiences[0].skills[1]: 'postgres' is an alias; use the canonical skill key 'postgresql'",
                "me.yaml:skills[0].skill: unknown skill 'cobol' (add it to the skill catalog first)");
    }

    @Test
    void validationMessagesNeverEchoPersonalData() {
        String yaml = CandidateYaml.profile(key).replace("end: 2022-12", "end: 2018-12");
        ConfigImportReport report = importAs(yaml, false);
        assertThat(report.issues()).singleElement().satisfies(i -> assertThat(i.message()).isEqualTo("experience 'exp-acme' ends before it starts"));
        assertThat(report.render()).doesNotContain("Jane", "jane.example", "Example GmbH");
    }

    @Test
    void snapshotsAreEncryptedAtRestAndBoundToTheirRow() {
        importAs(CandidateYaml.profile(key), true);
        List<String> stored = jdbc.queryForList("select v.snapshot::text from candidate_profile_version v "
                + "join candidate_profile p on p.id = v.candidate_profile_id where p.key = ? and p.owner_user_id = ?", String.class, key, user);

        assertThat(stored).singleElement().asString()
                .contains("AES-256-GCM").doesNotContain("Jane", "jane.example@example.com", "Example GmbH", "citizen");
        assertThat(detail().snapshot().contact().email()).isEqualTo("jane.example@example.com");

        UUID versionId = detail().currentVersion().versionId();
        UUID copyId = UUID.randomUUID();
        jdbc.update("insert into candidate_profile_version (id, owner_user_id, candidate_profile_id, version_no, schema_version, snapshot, "
                + "content_hash, created_at, updated_at, version) select ?, owner_user_id, candidate_profile_id, 99, schema_version, snapshot, "
                + "content_hash, now(), now(), 0 from candidate_profile_version where id = ?", copyId, versionId);
        assertThatThrownBy(() -> UserContext.callAs(user, () -> candidates.findVersion(copyId)))
                .as("ciphertext copied to another row cannot be decrypted").hasMessageContaining("altered, moved");
    }

    @Test
    void candidateProfilesAreRejectedFromTheConfigurationDirectory() throws Exception {
        Files.writeString(temp.resolve("oops.yaml"), CandidateYaml.profile(key));
        ConfigImportReport report = configImports.validate(temp);
        assertThat(report.issues()).singleElement().satisfies(i -> assertThat(i.message()).contains("candidate profiles are personal data and must not be stored"));
    }
}
