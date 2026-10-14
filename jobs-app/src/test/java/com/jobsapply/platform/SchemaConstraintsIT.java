package com.jobsapply.platform;

import static com.jobsapply.platform.support.DbAssertions.assertRejectedBy;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.jobsapply.platform.support.IntegrationTest;
import com.jobsapply.platform.support.TestData;
import com.jobsapply.platform.support.TestData.CandidateRefs;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Database-enforced integrity, exercised with plain SQL against real PostgreSQL. */
@IntegrationTest
class SchemaConstraintsIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestData data;

    @Test
    void migrationsAppliedInOrder() {
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Nested
    class Catalog {

        @Test
        void employerKeyIsUnique() {
            String key = uniqueKey("acme");
            data.employer(key);
            assertRejectedBy(() -> data.employer(key), "uk_employer_key");
        }

        @Test
        void employerKeyMustBeASlug() {
            assertRejectedBy(() -> data.employer("Not A Slug"), "ck_employer_key");
        }

        @Test
        void boardTokenOnASourceBelongsToOneEmployer() {
            UUID source = data.jobSource(uniqueKey("gh"));
            data.employerSource(data.employer(uniqueKey("a")), source, "token");
            assertRejectedBy(() -> data.employerSource(data.employer(uniqueKey("b")), source, "token"), "uk_employer_source_ref");
        }

        @Test
        void listingIsUniquePerBindingAndExternalId() {
            UUID employer = data.employer(uniqueKey("acme"));
            UUID binding = data.employerSource(employer, data.jobSource(uniqueKey("gh")), uniqueKey("tok"));
            UUID posting = data.posting(employer);
            data.listing(binding, posting, "job-1");
            assertRejectedBy(() -> data.listing(binding, posting, "job-1"), "uk_job_listing_external");
        }

        @Test
        void skillAliasResolvesToExactlyOneSkill() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            for (UUID id : new UUID[] {a, b}) {
                jdbc.update("insert into skill (id, key, display_name, created_at, updated_at, version) values (?, ?, 'x', now(), now(), 0)",
                        id, uniqueKey("s"));
            }
            String alias = uniqueKey("alias");
            String insert = "insert into skill_alias (id, skill_id, alias_normalized, created_at, updated_at, version) values (?, ?, ?, now(), now(), 0)";
            jdbc.update(insert, UUID.randomUUID(), a, alias);
            assertRejectedBy(() -> jdbc.update(insert, UUID.randomUUID(), b, alias), "uk_skill_alias_alias");
            assertRejectedBy(() -> jdbc.update(insert, UUID.randomUUID(), b, "Not Normalized"), "ck_skill_alias_normalized");
        }

        @Test
        void salaryRangeMustBeOrderedAndHaveACurrency() {
            UUID posting = data.posting(data.employer(uniqueKey("acme")));
            assertRejectedBy(() -> jdbc.update("update job_posting set salary_min = 10, salary_max = 5, salary_currency = 'USD' where id = ?", posting),
                    "ck_job_posting_salary_range");
            assertRejectedBy(() -> jdbc.update("update job_posting set salary_min = 10 where id = ?", posting),
                    "ck_job_posting_salary_currency");
        }
    }

    @Nested
    class Discovery {

        private static final String START = "insert into sync_run (id, job_source_id, employer_source_id, trigger, dry_run, status, "
                + "started_at, created_at, updated_at, version) values (?, ?, ?, 'MANUAL', false, 'RUNNING', now(), now(), now(), 0)";

        @Test
        void onlyOneRunningSyncPerBinding() {
            UUID source = data.jobSource(uniqueKey("gh"));
            UUID binding = data.employerSource(data.employer(uniqueKey("acme")), source, uniqueKey("tok"));
            UUID first = UUID.randomUUID();
            jdbc.update(START, first, source, binding);
            assertRejectedBy(() -> jdbc.update(START, UUID.randomUUID(), source, binding), "uk_sync_run_running_binding");

            jdbc.update("update sync_run set status = 'SUCCEEDED', finished_at = now() where id = ?", first);
            assertThatCode(() -> jdbc.update(START, UUID.randomUUID(), source, binding)).doesNotThrowAnyException();
        }

        @Test
        void onlyOneRunningSourceWideSync() {
            UUID source = data.jobSource(uniqueKey("gh"));
            jdbc.update(START, UUID.randomUUID(), source, null);
            assertRejectedBy(() -> jdbc.update(START, UUID.randomUUID(), source, null), "uk_sync_run_running_source");
        }

        @Test
        void finishedRunMustHaveFinishTime() {
            UUID source = data.jobSource(uniqueKey("gh"));
            UUID run = UUID.randomUUID();
            jdbc.update(START, run, source, null);
            assertRejectedBy(() -> jdbc.update("update sync_run set status = 'FAILED' where id = ?", run), "ck_sync_run_finished");
        }
    }

    @Nested
    class Profiles {

        @Test
        void roleProfileKeyIsUniquePerOwnerIncludingSystemTemplates() {
            UUID family = data.jobFamily(uniqueKey("fam"));
            UUID user = data.user(uniqueKey("user"));
            String key = uniqueKey("backend");
            data.roleProfileVersion(null, family, key);
            data.roleProfileVersion(user, family, key); // a user's fork may reuse a template's key
            assertRejectedBy(() -> data.roleProfileVersion(null, family, key), "uk_role_profile_owner_key");
            assertRejectedBy(() -> data.roleProfileVersion(user, family, key), "uk_role_profile_owner_key");
        }

        @Test
        void profileVersionsAreImmutable() {
            UUID version = data.roleProfileVersion(null, data.jobFamily(uniqueKey("fam")), uniqueKey("role"));
            assertRejectedBy(() -> jdbc.update("update role_profile_version set definition = '{\"x\":1}' where id = ?", version),
                    "rows are immutable");

            CandidateRefs candidate = data.candidate(data.user(uniqueKey("user")), "main");
            assertRejectedBy(() -> jdbc.update("update candidate_profile_version set note = 'x' where id = ?", candidate.versionId()),
                    "rows are immutable");
        }

        @Test
        void candidateVersionMustShareItsProfilesOwner() {
            UUID alice = data.user(uniqueKey("alice"));
            UUID bob = data.user(uniqueKey("bob"));
            CandidateRefs alices = data.candidate(alice, "main");
            assertRejectedBy(() -> jdbc.update("insert into candidate_profile_version (id, owner_user_id, candidate_profile_id, version_no, "
                    + "schema_version, snapshot, content_hash, created_at, updated_at, version) values (?, ?, ?, 2, 1, '{}', 'h', now(), now(), 0)",
                    UUID.randomUUID(), bob, alices.profileId()), "fk_candidate_profile_version_profile");
        }

        @Test
        void activeRoleVersionMustBelongToTheProfile() {
            UUID family = data.jobFamily(uniqueKey("fam"));
            UUID otherProfilesVersion = data.roleProfileVersion(null, family, uniqueKey("a"));
            data.roleProfileVersion(null, family, uniqueKey("b"));
            assertRejectedBy(() -> jdbc.update("update role_profile set active_version_id = ? where id <> "
                    + "(select role_profile_id from role_profile_version where id = ?)", otherProfilesVersion, otherProfilesVersion),
                    "fk_role_profile_active_version");
        }
    }

    @Nested
    class Applications {

        private record Fixture(UUID user, UUID posting, CandidateRefs main, CandidateRefs alt, UUID roleVersion) {}

        private Fixture fixture() {
            UUID user = data.user(uniqueKey("user"));
            UUID posting = data.posting(data.employer(uniqueKey("acme")));
            return new Fixture(user, posting, data.candidate(user, "main"), data.candidate(user, "alt"),
                    data.roleProfileVersion(null, data.jobFamily(uniqueKey("fam")), uniqueKey("role")));
        }

        @Test
        void atMostOneLiveApplicationPerUserAndPostingAcrossCandidateProfiles() {
            Fixture f = fixture();
            data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "SUBMITTED");
            assertRejectedBy(() -> data.application(f.user(), f.posting(), f.alt(), f.roleVersion(), "APPROVED"),
                    "uk_application_live_per_posting");
            // Preparing with another profile is allowed; only approval/submission is exclusive.
            assertThatCode(() -> data.application(f.user(), f.posting(), f.alt(), f.roleVersion(), "PREPARED"))
                    .doesNotThrowAnyException();
        }

        @Test
        void unconfirmedSubmissionAlsoBlocksAnotherSubmission() {
            Fixture f = fixture();
            data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "SUBMISSION_UNCONFIRMED");
            assertRejectedBy(() -> data.application(f.user(), f.posting(), f.alt(), f.roleVersion(), "SUBMITTING"),
                    "uk_application_live_per_posting");
        }

        @Test
        void atMostOneOpenApplicationPerProfileButClosedOnesDoNotBlock() {
            Fixture f = fixture();
            data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "REJECTED_BY_USER");
            data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "MATCHED");
            assertRejectedBy(() -> data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "DISCOVERED"),
                    "uk_application_open_per_profile");
        }

        @Test
        void differentUsersMayApplyToTheSamePosting() {
            Fixture f = fixture();
            UUID other = data.user(uniqueKey("other"));
            data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "SUBMITTED");
            assertThatCode(() -> data.application(other, f.posting(), data.candidate(other, "main"), f.roleVersion(), "SUBMITTED"))
                    .doesNotThrowAnyException();
        }

        @Test
        void applicationCannotUseAnotherUsersCandidateProfile() {
            Fixture f = fixture();
            UUID intruder = data.user(uniqueKey("intruder"));
            assertRejectedBy(() -> data.application(intruder, f.posting(), f.main(), f.roleVersion(), "DISCOVERED"),
                    "fk_application_candidate");
        }

        @Test
        void destinationListingMustBelongToThePosting() {
            Fixture f = fixture();
            UUID employer = data.employer(uniqueKey("other"));
            UUID otherPosting = data.posting(employer);
            UUID listing = data.listing(data.employerSource(employer, data.jobSource(uniqueKey("gh")), uniqueKey("t")), otherPosting, "x");
            UUID app = data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "PREPARED");
            assertRejectedBy(() -> jdbc.update("update application set job_listing_id = ? where id = ?", listing, app),
                    "fk_application_listing");
        }

        @Test
        void unknownStatusIsRejected() {
            Fixture f = fixture();
            assertRejectedBy(() -> data.application(f.user(), f.posting(), f.main(), f.roleVersion(), "SENT"), "ck_application_status");
        }
    }

    @Nested
    class Audit {

        private UUID event(UUID actor) {
            UUID id = UUID.randomUUID();
            jdbc.update("insert into audit_event (id, occurred_at, actor_type, actor_user_id, aggregate_type, event_type, payload, "
                    + "created_at, updated_at, version) values (?, now(), 'USER', ?, 'application', 'approved', '{\"k\":1}', now(), now(), 0)",
                    id, actor);
            return id;
        }

        @Test
        void auditLogIsAppendOnly() {
            UUID id = event(UUID.randomUUID());
            assertRejectedBy(() -> jdbc.update("update audit_event set payload = '{}' where id = ?", id), "append-only");
            assertRejectedBy(() -> jdbc.update("delete from audit_event where id = ?", id), "append-only");
        }

        @Test
        void actorMayOnlyBePseudonymized() {
            UUID id = event(UUID.randomUUID());
            assertThatCode(() -> jdbc.update("update audit_event set actor_user_id = null where id = ?", id)).doesNotThrowAnyException();
            assertRejectedBy(() -> jdbc.update("update audit_event set actor_user_id = gen_random_uuid() where id = ?", id), "append-only");
        }
    }
}
