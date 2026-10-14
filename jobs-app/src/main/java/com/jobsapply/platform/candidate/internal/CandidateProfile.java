package com.jobsapply.platform.candidate.internal;

import com.jobsapply.platform.shared.persistence.OwnedEntity;
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

@Entity
@Table(name = "candidate_profile")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CandidateProfile extends OwnedEntity {

    @Column(name = "key", nullable = false, updatable = false, length = 63)
    private String key;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public void makeCurrent(UUID versionId) {
        this.currentVersionId = Objects.requireNonNull(versionId);
    }
}
