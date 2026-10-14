package com.jobsapply.platform.catalog.internal;

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

@Entity
@Table(name = "job_family")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobFamily extends BaseEntity {

    @Column(name = "key", nullable = false, updatable = false, length = 63)
    private String key;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** @return whether anything changed */
    public boolean update(String displayName, String description) {
        if (Objects.equals(this.displayName, displayName) && Objects.equals(this.description, description)) {
            return false;
        }
        this.displayName = displayName;
        this.description = description;
        return true;
    }
}
