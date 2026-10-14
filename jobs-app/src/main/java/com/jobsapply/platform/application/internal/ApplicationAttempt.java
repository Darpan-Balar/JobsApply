package com.jobsapply.platform.application.internal;

import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.platform.application.SubmissionChannel;
import com.jobsapply.platform.shared.persistence.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One submission attempt, started by consuming an approval. Evidence is stored encrypted. */
@Entity
@Table(name = "application_attempt")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationAttempt extends OwnedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "approval_id", nullable = false, updatable = false)
    private UUID approvalId;

    @Column(name = "package_id", nullable = false, updatable = false)
    private UUID packageId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 24)
    private SubmissionChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private AttemptOutcome outcome;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Encrypted envelope (see {@code PersonalDataCipher}); confirmation text may contain personal data. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence")
    private String evidenceJson;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public void finish(AttemptOutcome result, String evidenceJson, Instant now) {
        if (outcome != AttemptOutcome.IN_PROGRESS) {
            throw new IllegalStateException("attempt already finished: " + outcome);
        }
        this.outcome = result;
        this.evidenceJson = evidenceJson;
        this.finishedAt = now;
    }

    public void verify(AttemptOutcome result, UUID by, String evidenceJson, Instant now) {
        this.outcome = result;
        this.verifiedBy = by;
        this.verifiedAt = now;
        if (evidenceJson != null) {
            this.evidenceJson = evidenceJson;
        }
    }
}
