package com.jobsapply.platform.source.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A configured instance of a job-source adapter plugin, e.g. {@code greenhouse-public → greenhouse-job-board}. */
@Entity
@Table(name = "job_source")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobSource extends BaseEntity {

    @Column(name = "key", nullable = false, updatable = false, length = 63)
    private String key;

    @Column(name = "adapter_id", nullable = false, length = 100)
    private String adapterId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Canonical JSON object; holds secret references only, never secret values. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false)
    private String settingsJson = "{}";

    @Builder.Default
    @Column(name = "settings_schema_version", nullable = false)
    private int settingsSchemaVersion = 1;

    /** @return whether anything changed */
    public boolean update(String adapterId, String displayName, boolean enabled, String settingsJson) {
        if (Objects.equals(this.adapterId, adapterId) && Objects.equals(this.displayName, displayName)
                && this.enabled == enabled && Objects.equals(this.settingsJson, settingsJson)) {
            return false;
        }
        this.adapterId = adapterId;
        this.displayName = displayName;
        this.enabled = enabled;
        this.settingsJson = settingsJson;
        return true;
    }
}
