package com.jobsapply.platform.tracking.internal;

import com.jobsapply.platform.shared.persistence.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Someone met during the search (recruiter, interviewer, referrer). Everything identifying is in the encrypted details. */
@Entity
@Table(name = "contact")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Contact extends OwnedEntity {

    @Column(name = "employer_id")
    private UUID employerId;

    /** Encrypted envelope (see {@code PersonalDataCipher}). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String details;

    public void update(UUID newEmployerId, String newDetails) {
        this.employerId = newEmployerId;
        this.details = newDetails;
    }
}
