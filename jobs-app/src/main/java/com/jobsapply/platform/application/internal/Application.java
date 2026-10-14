package com.jobsapply.platform.application.internal;

import com.jobsapply.platform.application.ApplicationStatus;
import com.jobsapply.platform.shared.error.InvalidStateTransitionException;
import com.jobsapply.platform.shared.model.Actor;
import com.jobsapply.platform.shared.persistence.OwnedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregate root of one application. The builder is private: applications start through {@link #discover} and change
 * state only through {@link #transitionTo}, which validates the move and appends history atomically with the change.
 */
@Entity
@Table(name = "application")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Application extends OwnedEntity {

    @Column(name = "job_posting_id", nullable = false, updatable = false)
    private UUID jobPostingId;

    @Column(name = "job_listing_id")
    private UUID jobListingId;

    @Column(name = "candidate_profile_id", nullable = false, updatable = false)
    private UUID candidateProfileId;

    @Column(name = "candidate_profile_version_id", nullable = false)
    private UUID candidateProfileVersionId;

    @Column(name = "role_profile_version_id", nullable = false)
    private UUID roleProfileVersionId;

    @Column(name = "job_match_id")
    private UUID jobMatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ApplicationStatus status;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    @OrderBy("occurredAt asc")
    @OneToMany(mappedBy = "application", cascade = CascadeType.PERSIST)
    private List<ApplicationStatusHistory> history = new ArrayList<>();

    public static Application discover(
            UUID jobPostingId,
            UUID candidateProfileId,
            UUID candidateProfileVersionId,
            UUID roleProfileVersionId,
            Actor actor,
            Instant now) {
        Application application = Application.builder()
                .jobPostingId(Objects.requireNonNull(jobPostingId))
                .candidateProfileId(Objects.requireNonNull(candidateProfileId))
                .candidateProfileVersionId(Objects.requireNonNull(candidateProfileVersionId))
                .roleProfileVersionId(Objects.requireNonNull(roleProfileVersionId))
                .status(ApplicationStatus.DISCOVERED)
                .statusChangedAt(Objects.requireNonNull(now))
                .build();
        application.history.add(new ApplicationStatusHistory(application, null, ApplicationStatus.DISCOVERED, actor, "discovered", now));
        return application;
    }

    /**
     * Moves to {@code target}. Fails if the transition table forbids it or if a human decision is attempted by the
     * system.
     */
    public void transitionTo(ApplicationStatus target, Actor actor, String reason, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actor, "actor");
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Cannot move application from " + status + " to " + target);
        }
        if (status.requiresUserActor(target) && !actor.isUser()) {
            throw new InvalidStateTransitionException(
                    "Moving from " + status + " to " + target + " requires an explicit user action");
        }
        history.add(new ApplicationStatusHistory(this, status, target, actor, reason, now));
        this.status = target;
        this.statusChangedAt = now;
    }

    public List<ApplicationStatusHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
