package com.jobsapply.platform.discovery.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import com.jobsapply.spi.common.FailureKind;
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
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "sync_error")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SyncError extends BaseEntity {

    @Column(name = "sync_run_id", nullable = false, updatable = false)
    private UUID syncRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private FailureKind kind;

    @Column(name = "external_ref", length = 200)
    private String externalRef;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
