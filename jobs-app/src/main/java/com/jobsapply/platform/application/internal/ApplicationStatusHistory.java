package com.jobsapply.platform.application.internal;

import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.model.ActorType;
import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Append-only record of one state change (also enforced by a database trigger). */
@Entity
@Immutable
@Table(name = "application_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32, updatable = false)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32, updatable = false)
    private ApplicationStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "reason", length = 1000, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    ApplicationStatusHistory(
            Application application, ApplicationStatus from, ApplicationStatus to, Actor actor, String reason, Instant at) {
        this.application = application;
        this.fromStatus = from;
        this.toStatus = to;
        this.actorType = actor.type();
        this.actorUserId = actor.userId();
        this.reason = reason;
        this.occurredAt = at;
    }
}
