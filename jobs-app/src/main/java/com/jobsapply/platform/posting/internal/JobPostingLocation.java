package com.jobsapply.platform.posting.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A normalized location of a posting; {@code rawText} preserves what the source published. */
@Entity
@Table(name = "job_posting_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingLocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false, updatable = false)
    private JobPosting posting;

    @Column(name = "raw_text", nullable = false, length = 500)
    private String rawText;

    @Column(name = "city", length = 200)
    private String city;

    @Column(name = "region", length = 200)
    private String region;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "is_remote", nullable = false)
    private boolean remote;

    JobPostingLocation(JobPosting posting, String rawText, String city, String region, String countryCode, boolean remote) {
        this.posting = posting;
        this.rawText = rawText;
        this.city = city;
        this.region = region;
        this.countryCode = countryCode;
        this.remote = remote;
    }
}
