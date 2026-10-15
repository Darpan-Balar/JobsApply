package com.jobsapply.platform.tracking.internal;

import com.jobsapply.platform.shared.persistence.OwnedEntity;
import com.jobsapply.platform.tracking.InterviewKind;
import com.jobsapply.platform.tracking.InterviewStatus;
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

/** An interview for a submitted application. Location and notes are in the encrypted details. */
@Entity
@Table(name = "interview")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Interview extends OwnedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private InterviewKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InterviewStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String details;

    public void reschedule(Instant at, Integer minutes, InterviewKind newKind, String newDetails) {
        this.scheduledAt = at;
        this.durationMinutes = minutes;
        this.kind = newKind;
        this.details = newDetails;
    }

    public void mark(InterviewStatus newStatus) {
        this.status = newStatus;
    }
}
