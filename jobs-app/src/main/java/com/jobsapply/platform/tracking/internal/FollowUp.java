package com.jobsapply.platform.tracking.internal;

import com.jobsapply.platform.shared.persistence.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Something to do by a date, optionally about an application or a contact. The note is encrypted. */
@Entity
@Table(name = "follow_up")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FollowUp extends OwnedEntity {

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "done_at")
    private Instant doneAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String details;

    public void update(LocalDate due, UUID application, UUID contact, String newDetails) {
        this.dueOn = due;
        this.applicationId = application;
        this.contactId = contact;
        this.details = newDetails;
    }

    public void complete(Instant at) {
        this.doneAt = at;
    }

    public void reopen() {
        this.doneAt = null;
    }
}
