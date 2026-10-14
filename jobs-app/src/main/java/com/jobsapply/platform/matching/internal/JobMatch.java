package com.jobsapply.platform.matching.internal;

import com.jobsapply.platform.shared.persistence.OwnedEntity;
import com.jobsapply.spi.common.Eligibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Explainable match of one posting for one candidate version against one role version, per strategy version. */
@Entity
@Table(name = "job_match")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobMatch extends OwnedEntity {

    @Column(name = "job_posting_id", nullable = false, updatable = false)
    private UUID jobPostingId;

    @Column(name = "candidate_profile_version_id", nullable = false, updatable = false)
    private UUID candidateProfileVersionId;

    @Column(name = "role_profile_version_id", nullable = false, updatable = false)
    private UUID roleProfileVersionId;

    @Column(name = "strategy_key", nullable = false, updatable = false, length = 63)
    private String strategyKey;

    @Column(name = "strategy_version", nullable = false, updatable = false)
    private int strategyVersion;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility", nullable = false, length = 16)
    private Eligibility eligibility;

    /** Canonical JSON of the SPI {@code MatchResult}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = false)
    private String resultJson;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;
}
