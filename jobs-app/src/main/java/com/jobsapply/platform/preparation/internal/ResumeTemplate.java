package com.jobsapply.platform.preparation.internal;

import com.jobsapply.platform.shared.persistence.BaseEntity;
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

/** A system template (owner null) or a user's own. Packages snapshot the definition they used. */
@Entity
@Table(name = "resume_template")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResumeTemplate extends BaseEntity {

    @Column(name = "owner_user_id", updatable = false)
    private UUID ownerUserId;

    @Column(name = "key", nullable = false, updatable = false, length = 63)
    private String key;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false)
    private String definitionJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    public boolean replace(String displayName, String definitionJson, String contentHash) {
        if (this.contentHash.equals(contentHash) && this.displayName.equals(displayName)) {
            return false;
        }
        this.displayName = displayName;
        this.definitionJson = definitionJson;
        this.contentHash = contentHash;
        return true;
    }
}
