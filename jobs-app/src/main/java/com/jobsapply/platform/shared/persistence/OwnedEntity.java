package com.jobsapply.platform.shared.persistence;

import com.jobsapply.platform.shared.error.OwnershipViolationException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Base for strictly user-owned aggregates (ADR-0002). Hibernate sets {@code owner_user_id} from the current
 * {@link UserContext} on insert and restricts every query to it. As a second line of defence, loading an owned entity
 * outside its owner's context fails instead of returning another user's data.
 */
@Getter
@MappedSuperclass
public abstract class OwnedEntity extends BaseEntity {

    @TenantId
    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    /** The owner; before the first insert (when Hibernate assigns it) this is the bound user. */
    public UUID getOwnerUserIdOrCurrent() {
        return ownerUserId != null ? ownerUserId : UserContext.requireUserId();
    }

    @PostLoad
    void verifyOwnership() {
        UUID current = UserContext.requireUserId();
        if (!current.equals(ownerUserId)) {
            throw new OwnershipViolationException(getClass().getSimpleName());
        }
    }
}
