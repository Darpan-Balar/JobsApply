package com.jobsapply.platform.employer.internal;

import com.jobsapply.platform.employer.EmployerSpec;
import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "varchar(63)[]")
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

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    /**
     * Applies new details. {@code metadataJson == null} leaves metadata unchanged.
     *
     * @return whether anything changed
     */
    public boolean update(EmployerSpec spec, String normalizedName, String metadataJson) {
        boolean changed = !Objects.equals(displayName, spec.displayName())
                || !Objects.equals(this.normalizedName, normalizedName)
                || !Objects.equals(websiteUrl, spec.website())
                || !Objects.equals(careersUrl, spec.careersUrl())
                || !Objects.equals(industry, spec.industry())
                || !Objects.equals(tags, spec.tags())
                || !Objects.equals(headquarters, spec.headquarters())
                || discoveryPriority != spec.discoveryPriority().shortValue()
                || enabled != spec.enabled()
                || (metadataJson != null && !Objects.equals(this.metadataJson, metadataJson));
        if (!changed) {
            return false;
        }
        this.displayName = spec.displayName();
        this.normalizedName = normalizedName;
        this.websiteUrl = spec.website();
        this.careersUrl = spec.careersUrl();
        this.industry = spec.industry();
        this.tags = new ArrayList<>(spec.tags());
        this.headquarters = spec.headquarters();
        this.discoveryPriority = spec.discoveryPriority().shortValue();
        this.enabled = spec.enabled();
        if (metadataJson != null) {
            this.metadataJson = metadataJson;
        }
        return true;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
        }
    }

    public void unarchive() {
        archivedAt = null;
    }
}
