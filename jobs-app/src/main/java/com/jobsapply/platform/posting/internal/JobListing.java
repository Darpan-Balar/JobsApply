package com.jobsapply.platform.posting.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import com.jobsapply.spi.common.Availability;
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

/**
 * One source's record of a vacancy, unique per (employer source, external id). Dates are stored only when the source
 * supplied them; {@code firstSeenAt}/{@code lastSeenAt}/{@code lastCheckedAt} are the platform's own observations.
 */
@Entity
@Table(name = "job_listing")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobListing extends BaseEntity {

    /** References {@code employer_source.id} (source module: by id only). */
    @Column(name = "employer_source_id", nullable = false, updatable = false)
    private UUID employerSourceId;

    @Column(name = "job_posting_id", nullable = false)
    private UUID jobPostingId;

    @Column(name = "external_id", nullable = false, updatable = false, length = 200)
    private String externalId;

    @Column(name = "title_raw", nullable = false, length = 500)
    private String titleRaw;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "location_raw", nullable = false)
    private String locationRawJson = "[]";

    @Column(name = "department", length = 300)
    private String department;

    @Column(name = "employment_type_raw", length = 100)
    private String employmentTypeRaw;

    @Column(name = "listing_url", length = 2048)
    private String listingUrl;

    @Column(name = "application_url", length = 2048)
    private String applicationUrl;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 8)
    private Availability availability = Availability.UNKNOWN;

    @Column(name = "availability_evidence", columnDefinition = "text")
    private String availabilityEvidence;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata", nullable = false)
    private String sourceMetadataJson = "{}";
}
