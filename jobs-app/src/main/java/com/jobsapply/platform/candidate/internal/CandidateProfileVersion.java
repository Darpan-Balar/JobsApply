package com.jobsapply.platform.candidate.internal;

import com.jobsapply.platform.shared.persistence.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable snapshot of a candidate profile (also enforced by a database trigger). The snapshot is personal data and is
 * stored encrypted (AES-256-GCM, bound to this row and owner); {@code contentHash} is a keyed HMAC fingerprint.
 */
@Entity
@Immutable
@Table(name = "candidate_profile_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateProfileVersion extends OwnedEntity {

    @Column(name = "candidate_profile_id", nullable = false, updatable = false)
    private UUID candidateProfileId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, updatable = false)
    private String snapshotJson;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    @Column(name = "note", length = 1000, updatable = false)
    private String note;

    /**
     * @param encryptor turns the plaintext snapshot into stored text given this version's id (so the ciphertext can be
     *     bound to its row)
     */
    public static CandidateProfileVersion create(UUID candidateProfileId, int versionNo, int schemaVersion,
            Function<UUID, String> encryptor, String contentFingerprint, String note) {
        CandidateProfileVersion version = new CandidateProfileVersion();
        version.candidateProfileId = candidateProfileId;
        version.versionNo = versionNo;
        version.schemaVersion = schemaVersion;
        version.snapshotJson = encryptor.apply(version.getId());
        version.contentHash = contentFingerprint;
        version.note = note;
        return version;
    }
}
