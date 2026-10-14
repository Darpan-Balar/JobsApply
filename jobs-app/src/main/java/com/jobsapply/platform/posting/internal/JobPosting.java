package com.jobsapply.platform.posting.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.CompensationPeriod;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Canonical vacancy. Salary fields are set only when a source published them — never estimated. */
@Entity
@Table(name = "job_posting")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobPosting extends BaseEntity {

    @Column(name = "employer_id", nullable = false, updatable = false)
    private UUID employerId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "title_normalized", nullable = false, length = 500)
    private String titleNormalized;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "seniority", nullable = false, length = 16)
    private Seniority seniority = Seniority.UNKNOWN;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 16)
    private EmploymentType employmentType = EmploymentType.UNKNOWN;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "workplace_type", nullable = false, length = 16)
    private WorkplaceType workplaceType = WorkplaceType.UNKNOWN;

    @Column(name = "salary_min", precision = 14, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 14, scale = 2)
    private BigDecimal salaryMax;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 8)
    private CompensationPeriod salaryPeriod;

    @Column(name = "description_text", columnDefinition = "text")
    private String descriptionText;

    @Column(name = "dedup_fingerprint", nullable = false, length = 64)
    private String dedupFingerprint;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 8)
    private Availability availability = Availability.UNKNOWN;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "posting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingLocation> locations = new ArrayList<>();

    public List<JobPostingLocation> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    public JobPostingLocation addLocation(String rawText, String city, String region, String countryCode, boolean remote) {
        JobPostingLocation location = new JobPostingLocation(this, rawText, city, region, countryCode, remote);
        locations.add(location);
        return location;
    }
}
