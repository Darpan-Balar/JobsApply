package com.jobsapply.platform.support;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inserts catalog rows with plain SQL so constraint tests exercise the database itself. Keys are randomized so tests
 * are independent without cleanup.
 */
@Component
public class TestData {

    private final JdbcTemplate jdbc;

    public TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public UUID user(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (id, username, display_name, status, created_at, updated_at, version) "
                + "values (?, ?, ?, 'ACTIVE', now(), now(), 0)", id, username, username);
        return id;
    }

    public UUID employer(String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into employer (id, key, display_name, normalized_name, created_at, updated_at, version) "
                + "values (?, ?, ?, ?, now(), now(), 0)", id, key, key, key);
        return id;
    }

    public UUID jobSource(String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into job_source (id, key, adapter_id, display_name, created_at, updated_at, version) "
                + "values (?, ?, 'test-adapter', ?, now(), now(), 0)", id, key, key);
        return id;
    }

    public UUID employerSource(UUID employerId, UUID jobSourceId, String externalRef) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into employer_source (id, employer_id, job_source_id, external_ref, created_at, updated_at, version) "
                + "values (?, ?, ?, ?, now(), now(), 0)", id, employerId, jobSourceId, externalRef);
        return id;
    }

    public UUID posting(UUID employerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into job_posting (id, employer_id, title, title_normalized, dedup_fingerprint, first_seen_at, "
                + "last_seen_at, created_at, updated_at, version) values (?, ?, 'Backend Engineer', 'backend engineer', 'fp', "
                + "now(), now(), now(), now(), 0)", id, employerId);
        return id;
    }

    public UUID listing(UUID employerSourceId, UUID postingId, String externalId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into job_listing (id, employer_source_id, job_posting_id, external_id, title_raw, first_seen_at, "
                + "last_seen_at, last_checked_at, content_hash, created_at, updated_at, version) "
                + "values (?, ?, ?, ?, 'Backend Engineer', now(), now(), now(), 'hash', now(), now(), 0)",
                id, employerSourceId, postingId, externalId);
        return id;
    }

    public UUID jobFamily(String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into job_family (id, key, display_name, created_at, updated_at, version) values (?, ?, ?, now(), now(), 0)",
                id, key, key);
        return id;
    }

    /** Role profile with one version (active). Returns the version id. */
    public UUID roleProfileVersion(UUID ownerOrNull, UUID jobFamilyId, String key) {
        UUID profileId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("insert into role_profile (id, owner_user_id, job_family_id, key, display_name, created_at, updated_at, version) "
                + "values (?, ?, ?, ?, ?, now(), now(), 0)", profileId, ownerOrNull, jobFamilyId, key, key);
        jdbc.update("insert into role_profile_version (id, role_profile_id, version_no, schema_version, definition, content_hash, "
                + "created_at, updated_at, version) values (?, ?, 1, 1, '{}'::jsonb, ?, now(), now(), 0)",
                versionId, profileId, UUID.randomUUID().toString());
        return versionId;
    }

    public record CandidateRefs(UUID profileId, UUID versionId) {}

    public CandidateRefs candidate(UUID ownerId, String key) {
        UUID profileId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("insert into candidate_profile (id, owner_user_id, key, display_name, created_at, updated_at, version) "
                + "values (?, ?, ?, ?, now(), now(), 0)", profileId, ownerId, key, key);
        jdbc.update("insert into candidate_profile_version (id, owner_user_id, candidate_profile_id, version_no, schema_version, "
                + "snapshot, content_hash, created_at, updated_at, version) values (?, ?, ?, 1, 1, '{}'::jsonb, 'h', now(), now(), 0)",
                versionId, ownerId, profileId);
        return new CandidateRefs(profileId, versionId);
    }

    public UUID application(UUID ownerId, UUID postingId, CandidateRefs candidate, UUID roleVersionId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into application (id, owner_user_id, job_posting_id, candidate_profile_id, candidate_profile_version_id, "
                + "role_profile_version_id, status, status_changed_at, created_at, updated_at, version) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)",
                id, ownerId, postingId, candidate.profileId(), candidate.versionId(), roleVersionId, status,
                java.sql.Timestamp.from(Instant.now()));
        return id;
    }
}
