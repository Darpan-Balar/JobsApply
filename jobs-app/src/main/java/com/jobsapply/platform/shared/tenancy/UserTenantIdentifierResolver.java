package com.jobsapply.platform.shared.tenancy;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Supplies the current user as Hibernate's tenant identifier for {@code @TenantId} entities. Without a bound user it
 * returns {@link #NO_USER}, which matches no rows and cannot be inserted (no such user) — i.e. it fails closed.
 */
@Component
class UserTenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    static final UUID NO_USER = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return UserContext.currentUserId().orElse(NO_USER);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
