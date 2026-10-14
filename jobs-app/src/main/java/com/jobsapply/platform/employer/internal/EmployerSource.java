package com.jobsapply.platform.employer.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Binds an employer to a job source via the provider's identifier for that employer (board token, site name). */
@Entity
@Table(name = "employer_source")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmployerSource extends BaseEntity {

    /** References {@code employer.id} (same module). */
    @Column(name = "employer_id", nullable = false, updatable = false)
    private UUID employerId;

    /** References {@code job_source.id} (source module: by id only). */
    @Column(name = "job_source_id", nullable = false, updatable = false)
    private UUID jobSourceId;

    @Column(name = "external_ref", nullable = false, length = 200)
    private String externalRef;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "priority", nullable = false)
    private short priority = 50;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false)
    private String settingsJson = "{}";

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Builder.Default
    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    /** @return whether anything changed */
    public boolean configure(boolean enabled, int priority) {
        if (this.enabled == enabled && this.priority == priority) {
            return false;
        }
        this.enabled = enabled;
        this.priority = (short) priority;
        return true;
    }
}
