package com.jobsapply.platform.preparation.internal;

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

/** One immutable version of a prepared application (payload encrypted, bound to this row and owner). */
@Entity
@Immutable
@Table(name = "application_package")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationPackage extends OwnedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payloadJson;

    /** Keyed fingerprint of the package's substance (excludes timestamps and file references). */
    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    @Column(name = "blocking_issues", nullable = false, updatable = false)
    private int blockingIssues;

    @Column(name = "warnings", nullable = false, updatable = false)
    private int warnings;

    public static ApplicationPackage create(UUID applicationId, int versionNo, Function<UUID, String> encryptor, String contentHash,
            int blockingIssues, int warnings) {
        ApplicationPackage p = new ApplicationPackage();
        p.applicationId = applicationId;
        p.versionNo = versionNo;
        p.payloadJson = encryptor.apply(p.getId());
        p.contentHash = contentHash;
        p.blockingIssues = blockingIssues;
        p.warnings = warnings;
        return p;
    }
}
