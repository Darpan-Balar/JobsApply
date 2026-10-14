package com.jobsapply.platform.employer.internal;

import com.jobsapply.platform.employer.PreferenceMode;
import com.jobsapply.platform.shared.persistence.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A user's private stance on an employer (watch / ignore). */
@Entity
@Table(name = "user_employer_preference")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserEmployerPreference extends OwnedEntity {

    @Column(name = "employer_id", nullable = false, updatable = false)
    private UUID employerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 8)
    private PreferenceMode mode;

    @Column(name = "priority", nullable = false)
    private short priority;

    @Column(name = "note", length = 1000)
    private String note;

    public void change(PreferenceMode mode, int priority, String note) {
        this.mode = mode;
        this.priority = (short) priority;
        this.note = note;
    }
}
