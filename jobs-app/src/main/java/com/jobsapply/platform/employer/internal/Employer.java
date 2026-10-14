package com.jobsapply.platform.employer.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "employer")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Employer extends BaseEntity {

    @Column(name = "key", nullable = false, updatable = false, length = 63)
    private String key;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @Column(name = "careers_url", length = 2048)
    private String careersUrl;

    @Column(name = "industry", length = 100)
    private String industry;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "headquarters", length = 200)
    private String headquarters;

    /** Canonical JSON object (see {@code JsonCodec}). */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private String metadataJson = "{}";

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "discovery_priority", nullable = false)
    private short discoveryPriority = 50;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
