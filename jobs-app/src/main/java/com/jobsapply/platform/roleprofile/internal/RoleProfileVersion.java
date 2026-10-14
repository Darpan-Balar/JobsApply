package com.jobsapply.platform.roleprofile.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable (also enforced by a database trigger). */
@Entity
@Immutable
@Table(name = "role_profile_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleProfileVersion extends BaseEntity {

    @Column(name = "role_profile_id", nullable = false, updatable = false)
    private UUID roleProfileId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false, updatable = false)
    private String definitionJson;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    public static RoleProfileVersion of(
            UUID roleProfileId, int versionNo, int schemaVersion, String definitionJson, String contentHash) {
        RoleProfileVersion version = new RoleProfileVersion();
        version.roleProfileId = roleProfileId;
        version.versionNo = versionNo;
        version.schemaVersion = schemaVersion;
        version.definitionJson = definitionJson;
        version.contentHash = contentHash;
        return version;
    }
}
