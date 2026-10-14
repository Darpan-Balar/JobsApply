package com.jobsapply.platform.roleprofile.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Not an {@code OwnedEntity}: a NULL owner marks a system template visible to everyone, so visibility is enforced by
 * explicit repository queries rather than the tenant filter (ADR-0002).
 */
@Entity
@Table(name = "role_profile")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RoleProfile extends BaseEntity {

    @Column(name = "owner_user_id", updatable = false)
    private UUID ownerUserId;

    @Column(name = "job_family_id", nullable = false)
    private UUID jobFamilyId;

    @Column(name = "key", nullable = false, updatable = false, length = 63)
    private String key;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "active_version_id")
    private UUID activeVersionId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public void activate(UUID versionId) {
        this.activeVersionId = Objects.requireNonNull(versionId);
    }

    public void describe(String displayName, UUID jobFamilyId) {
        this.displayName = displayName;
        this.jobFamilyId = jobFamilyId;
    }
}
