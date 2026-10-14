package com.jobsapply.platform.shared.tenancy;

import com.jobsapply.platform.shared.error.MissingUserContextException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The user on whose behalf code runs, held in a {@link ScopedValue} (immutable, bounded to a call scope, not leaked
 * to pooled threads). Web requests bind it from authentication; background jobs must bind it explicitly.
 *
 * <p>Persistence sessions capture the user when they open (transaction start), so bind the context <em>before</em>
 * starting a transaction.
 */
public final class UserContext {

    private static final ScopedValue<UUID> CURRENT_USER = ScopedValue.newInstance();

    private UserContext() {}

    public static Optional<UUID> currentUserId() {
        return CURRENT_USER.isBound() ? Optional.of(CURRENT_USER.get()) : Optional.empty();
    }

    public static UUID requireUserId() {
        return currentUserId().orElseThrow(MissingUserContextException::new);
    }

    public static void runAs(UUID userId, Runnable action) {
        ScopedValue.where(CURRENT_USER, Objects.requireNonNull(userId, "userId")).run(action);
    }

    public static <T> T callAs(UUID userId, Supplier<T> action) {
        return ScopedValue.where(CURRENT_USER, Objects.requireNonNull(userId, "userId")).call(action::get);
    }

    /** Variant for code that throws checked exceptions (e.g. servlet filters). */
    public static <T, X extends Throwable> T callAsChecked(UUID userId, ScopedValue.CallableOp<T, X> action) throws X {
        return ScopedValue.where(CURRENT_USER, Objects.requireNonNull(userId, "userId")).call(action);
    }
}
