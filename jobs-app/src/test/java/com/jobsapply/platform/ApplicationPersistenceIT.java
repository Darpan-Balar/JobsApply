package com.jobsapply.platform;

import static com.jobsapply.platform.support.DbAssertions.assertRejectedBy;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.application.internal.Application;
import com.jobsapply.platform.application.internal.ApplicationRepository;
import com.jobsapply.platform.application.internal.ApplicationStatusHistory;
import com.jobsapply.platform.candidate.CandidateProfileRef;
import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.candidate.CandidateProfileVersionRef;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.TestData;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** The application aggregate round-trips through JPA with its history, under optimistic locking. */
@IntegrationTest
class ApplicationPersistenceIT {

    @Autowired UserService users;
    @Autowired CandidateProfileService candidates;
    @Autowired ApplicationRepository applications;
    @Autowired TestData data;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    UUID user;
    UUID posting;
    UUID roleVersion;
    CandidateProfileRef profile;
    CandidateProfileVersionRef version;
    final Instant t0 = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @BeforeEach
    void setUp() {
        user = users.createUser(NewUser.builder().username(uniqueKey("user")).displayName("User").build()).id();
        posting = data.posting(data.employer(uniqueKey("acme")));
        roleVersion = data.roleProfileVersion(null, data.jobFamily(uniqueKey("fam")), uniqueKey("role"));
        UserContext.runAs(user, () -> {
            profile = candidates.createProfile("main", "Main");
            version = candidates.addVersion(profile.id(), CandidateSnapshot.builder().build(), null);
        });
    }

    private UUID newApplication() {
        return UserContext.callAs(user, () -> tx.execute(s -> applications.save(Application.discover(
                posting, profile.id(), version.versionId(), roleVersion, Actor.system(), t0)).getId()));
    }

    private void transition(UUID id, ApplicationStatus target, Actor actor) {
        UserContext.runAs(user, () -> tx.executeWithoutResult(s ->
                applications.findById(id).orElseThrow().transitionTo(target, actor, "test", t0.plusSeconds(1))));
    }

    @Test
    void statusAndHistoryArePersistedWithOwner() {
        UUID id = newApplication();
        transition(id, ApplicationStatus.MATCHED, Actor.system());
        transition(id, ApplicationStatus.REJECTED_BY_USER, Actor.user(user));

        UserContext.runAs(user, () -> tx.executeWithoutResult(s -> {
            Application loaded = applications.findById(id).orElseThrow();
            assertThat(loaded.getOwnerUserId()).isEqualTo(user);
            assertThat(loaded.getStatus()).isEqualTo(ApplicationStatus.REJECTED_BY_USER);
            assertThat(loaded.getHistory()).extracting(ApplicationStatusHistory::getToStatus)
                    .containsExactly(ApplicationStatus.DISCOVERED, ApplicationStatus.MATCHED, ApplicationStatus.REJECTED_BY_USER);
            assertThat(loaded.getHistory().getLast().getActorUserId()).isEqualTo(user);
        }));
    }

    @Test
    void concurrentModificationIsDetected() {
        UUID id = newApplication();
        assertThatThrownBy(() -> UserContext.runAs(user, () -> tx.executeWithoutResult(outer -> {
            Application stale = applications.findById(id).orElseThrow();
            transitionInNewTransaction(id);
            stale.transitionTo(ApplicationStatus.NOT_ELIGIBLE, Actor.system(), "stale writer", t0);
        }))).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private void transitionInNewTransaction(UUID id) {
        TransactionTemplate requiresNew = new TransactionTemplate(tx.getTransactionManager());
        requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(s ->
                applications.findById(id).orElseThrow().transitionTo(ApplicationStatus.MATCHED, Actor.system(), "first writer", t0));
    }

    @Test
    void historyRowsAreImmutable() {
        UUID id = newApplication();
        assertRejectedBy(() -> jdbc.update("update application_status_history set reason = 'rewritten' where application_id = ?", id),
                "rows are immutable");
    }

    @Test
    void secondLiveApplicationIsRejectedThroughJpaToo() {
        UUID first = newApplication();
        for (ApplicationStatus step : new ApplicationStatus[] {ApplicationStatus.MATCHED, ApplicationStatus.PREPARED,
                ApplicationStatus.READY_FOR_REVIEW}) {
            transition(first, step, Actor.system());
        }
        transition(first, ApplicationStatus.APPROVED, Actor.user(user));

        CandidateProfileRef other = UserContext.callAs(user, () -> candidates.createProfile("alt", "Alt"));
        CandidateProfileVersionRef otherVersion = UserContext.callAs(user,
                () -> candidates.addVersion(other.id(), CandidateSnapshot.builder().build(), null));
        UUID second = UserContext.callAs(user, () -> tx.execute(s -> applications.save(Application.discover(
                posting, other.id(), otherVersion.versionId(), roleVersion, Actor.system(), t0)).getId()));
        for (ApplicationStatus step : new ApplicationStatus[] {ApplicationStatus.MATCHED, ApplicationStatus.PREPARED,
                ApplicationStatus.READY_FOR_REVIEW}) {
            transition(second, step, Actor.system());
        }

        assertThatThrownBy(() -> transition(second, ApplicationStatus.APPROVED, Actor.user(user)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_application_live_per_posting");
    }
}
