package com.jobsapply.platform.application.internal;

import com.jobsapply.platform.shared.persistence.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The candidate's approval of one exact package for one submission. Single use. */
@Entity
@Table(name = "submission_approval")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SubmissionApproval extends OwnedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "package_id", nullable = false, updatable = false)
    private UUID packageId;

    @Column(name = "package_hash", nullable = false, updatable = false, length = 64)
    private String packageHash;

    @Column(name = "approved_by", nullable = false, updatable = false)
    private UUID approvedBy;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private Instant approvedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 200)
    private String revokeReason;

    public boolean isActive() {
        return consumedAt == null && revokedAt == null;
    }

    public void consume(Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("approval is no longer usable");
        }
        this.consumedAt = now;
    }

    public void revoke(Instant now, String reason) {
        if (isActive()) {
            this.revokedAt = now;
            this.revokeReason = reason;
        }
    }
}
