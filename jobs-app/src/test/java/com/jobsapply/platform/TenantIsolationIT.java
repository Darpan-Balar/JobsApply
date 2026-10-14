package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.internal.Application;
import com.jobsapply.platform.application.internal.ApplicationRepository;
import com.jobsapply.platform.candidate.CandidateProfileRef;
import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.candidate.CandidateProfileVersionRef;
import com.jobsapply.platform.candidate.internal.CandidateProfile;
import com.jobsapply.platform.candidate.internal.CandidateProfileRepository;
import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.catalog.JobFamilySpec;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.roleprofile.RoleProfileImportResult;
import com.jobsapply.platform.roleprofile.RoleProfileScope;
import com.jobsapply.platform.roleprofile.RoleProfileService;
import com.jobsapply.platform.shared.error.MissingUserContextException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.error.OwnershipViolationException;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.TestData;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** ADR-0002: a user can see and change only their own personal data, through every access path. */
@IntegrationTest
class TenantIsolationIT {

    @Autowired UserService users;
    @Autowired CandidateProfileService candidates;
    @Autowired CandidateProfileRepository candidateRepository;
    @Autowired ApplicationRepository applications;
    @Autowired RoleProfileService roleProfiles;
    @Autowired CatalogService catalog;
    @Autowired TestData data;
    @Autowired TransactionTemplate tx;

    UUID alice;
    UUID bob;
    CandidateProfileRef alicesProfile;
    CandidateProfileVersionRef alicesVersion;
    UUID alicesApplication;

    @BeforeEach
    void setUp() {
        alice = users.createUser(NewUser.builder().username(uniqueKey("alice")).displayName("Alice").build()).id();
        bob = users.createUser(NewUser.builder().username(uniqueKey("bob")).displayName("Bob").build()).id();

        UserContext.runAs(alice, () -> {
            alicesProfile = candidates.createProfile("main", "Main profile");
            alicesVersion = candidates.addVersion(alicesProfile.id(), snapshot(), "initial");
        });

        UUID posting = data.posting(data.employer(uniqueKey("acme")));
        UUID roleVersion = data.roleProfileVersion(null, data.jobFamily(uniqueKey("fam")), uniqueKey("role"));
        alicesApplication = UserContext.callAs(alice, () -> tx.execute(status -> applications.save(Application.discover(
                posting, alicesProfile.id(), alicesVersion.versionId(), roleVersion, Actor.system(), Instant.now())).getId()));
    }

    private static CandidateSnapshot snapshot() {
        return CandidateSnapshot.builder()
                .contact(CandidateContact.builder().fullName("Alice Example").email("alice@example.com").build())
                .skills(List.of(CandidateSkill.builder().id("skill-java").verification(VerificationStatus.VERIFIED).skill("java").build()))
                .build();
    }

    @Test
    void ownerSeesTheirData() {
        UserContext.runAs(alice, () -> {
            assertThat(candidates.listProfiles()).extracting(CandidateProfileRef::id).containsExactly(alicesProfile.id());
            assertThat(candidates.findVersion(alicesVersion.versionId())).isPresent()
                    .get().satisfies(v -> assertThat(v.snapshot().contact().email()).isEqualTo("alice@example.com"));
            assertThat(inTx(() -> applications.findById(alicesApplication))).isPresent();
        });
    }

    @Test
    void otherUserCannotListOrQueryTheData() {
        UserContext.runAs(bob, () -> {
            assertThat(candidates.listProfiles()).isEmpty();
            assertThat(inTx(() -> candidateRepository.findAll())).isEmpty();
            assertThat(inTx(() -> applications.findAll())).isEmpty();
            assertThat(inTx(() -> candidateRepository.findByKey("main"))).isEmpty();
        });
    }

    @Test
    void otherUserCannotLoadByIdentifier() {
        UserContext.runAs(bob, () -> {
            assertThatOwnedDataIsInaccessible(() -> candidates.findVersion(alicesVersion.versionId()));
            assertThatOwnedDataIsInaccessible(() -> inTx(() -> applications.findById(alicesApplication)));
            assertThatOwnedDataIsInaccessible(() -> inTx(() -> candidateRepository.findById(alicesProfile.id())));
        });
    }

    @Test
    void otherUserCannotModifyTheData() {
        UserContext.runAs(bob, () -> assertThatThrownBy(() -> candidates.addVersion(alicesProfile.id(), snapshot(), "hijack"))
                .isInstanceOfAny(NotFoundException.class, OwnershipViolationException.class));
        UserContext.runAs(alice, () -> assertThat(candidates.findVersion(alicesVersion.versionId()))
                .get().satisfies(v -> assertThat(v.ref().versionNo()).isEqualTo(1)));
    }

    @Test
    void usersMayUseTheSameProfileKeyIndependently() {
        UserContext.runAs(bob, () -> assertThat(candidates.createProfile("main", "Bob's main").id()).isNotEqualTo(alicesProfile.id()));
    }

    @Test
    void missingUserContextFailsClosed() {
        assertThatThrownBy(() -> candidates.listProfiles()).isInstanceOf(MissingUserContextException.class);
        assertThat(inTx(() -> applications.findAll())).as("unscoped repository access sees nothing").isEmpty();
        assertThatOwnedDataIsInaccessible(() -> inTx(() -> applications.findById(alicesApplication)));
    }

    @Test
    void ownedRowsCannotBeWrittenWithoutAUser() {
        assertThatThrownBy(() -> inTx(() -> candidateRepository.saveAndFlush(
                com.jobsapply.platform.candidate.internal.CandidateProfile.builder().key("orphan").displayName("x").build())))
                .isNotNull();
    }

    @Test
    void userOwnedRoleProfilesArePrivateButTemplatesAreShared() {
        String family = uniqueKey("fam");
        catalog.upsertJobFamily(new JobFamilySpec(family, "Family", null));
        String templateKey = uniqueKey("template");
        String privateKey = uniqueKey("private");
        roleProfiles.importDefinition(role(templateKey, family), 1, RoleProfileScope.SYSTEM_TEMPLATE);
        RoleProfileImportResult own = UserContext.callAs(alice,
                () -> roleProfiles.importDefinition(role(privateKey, family), 1, RoleProfileScope.CURRENT_USER));

        UserContext.runAs(alice, () -> {
            assertThat(roleProfiles.findVisible(privateKey)).isPresent().get().satisfies(p -> assertThat(p.systemTemplate()).isFalse());
            assertThat(roleProfiles.findVisible(templateKey)).isPresent();
        });
        UserContext.runAs(bob, () -> {
            assertThat(roleProfiles.findVisible(privateKey)).isEmpty();
            assertThat(roleProfiles.findVisible(templateKey)).isPresent().get().satisfies(p -> assertThat(p.systemTemplate()).isTrue());
            assertThat(roleProfiles.listVisible()).extracting(p -> p.activeVersion().versionId())
                    .doesNotContain(own.version().versionId());
        });
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    /**
     * Hibernate's tenant filter hides other users' rows even for loads by id (verified with Hibernate 7.4), so the
     * result must be empty. The {@code OwnedEntity} post-load guard is a second line of defence (see
     * {@code OwnedEntityGuardTest}); if this ever starts throwing instead, the filter behavior changed: investigate.
     */
    private static void assertThatOwnedDataIsInaccessible(Supplier<Optional<?>> call) {
        assertThat(call.get()).isEmpty();
    }

    private static RoleProfileDefinition role(String key, String family) {
        return RoleProfileDefinition.builder()
                .key(key)
                .displayName(key)
                .jobFamily(family)
                .titles(RoleProfileDefinition.TitleRules.builder().include(List.of("engineer")).build())
                .skills(RoleProfileDefinition.SkillRequirements.builder().build())
                .scoring(RoleProfileDefinition.ScoringConfig.builder()
                        .strategy("weighted")
                        .strategyVersion(1)
                        .weights(Map.of("title", BigDecimal.ONE))
                        .thresholds(new RoleProfileDefinition.Thresholds(BigDecimal.ZERO, BigDecimal.TEN))
                        .build())
                .build();
    }
}
