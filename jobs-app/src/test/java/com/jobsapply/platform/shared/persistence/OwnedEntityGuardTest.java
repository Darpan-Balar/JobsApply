package com.jobsapply.platform.shared.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.shared.error.MissingUserContextException;
import com.jobsapply.platform.shared.error.OwnershipViolationException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Second line of defence behind the tenant filter: an owned entity never materializes outside its owner's context. */
class OwnedEntityGuardTest {

    static class SomeOwned extends OwnedEntity {}

    private static OwnedEntity ownedBy(UUID owner) throws Exception {
        OwnedEntity entity = new SomeOwned();
        Field field = OwnedEntity.class.getDeclaredField("ownerUserId");
        field.setAccessible(true);
        field.set(entity, owner);
        return entity;
    }

    @Test
    void ownerMayLoad() throws Exception {
        UUID owner = UUID.randomUUID();
        OwnedEntity entity = ownedBy(owner);
        UserContext.runAs(owner, () -> assertThatCode(entity::verifyOwnership).doesNotThrowAnyException());
    }

    @Test
    void anotherUserMayNotLoad() throws Exception {
        OwnedEntity entity = ownedBy(UUID.randomUUID());
        UserContext.runAs(UUID.randomUUID(), () ->
                assertThatThrownBy(entity::verifyOwnership).isInstanceOf(OwnershipViolationException.class));
    }

    @Test
    void loadingWithoutUserContextFails() throws Exception {
        OwnedEntity entity = ownedBy(UUID.randomUUID());
        assertThatThrownBy(entity::verifyOwnership).isInstanceOf(MissingUserContextException.class);
    }
}
