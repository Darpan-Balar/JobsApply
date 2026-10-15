package com.jobsapply.platform.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Failed sign-ins per username (in memory; a restart clears it). After {@code maxFailedLogins} failures within the
 * lockout window the username is locked for {@code lockout}. Unknown usernames are counted the same way, so the
 * response does not reveal which usernames exist.
 */
@Component
class LoginAttempts {

    private static final int MAX_TRACKED = 10_000;

    private record Failures(int count, Instant first, Instant lockedUntil) {}

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final IdentityProperties properties;
    private final Clock clock;

    LoginAttempts(IdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    boolean isLocked(String username) {
        Failures f = failures.get(key(username));
        return f != null && f.lockedUntil() != null && clock.instant().isBefore(f.lockedUntil());
    }

    void failed(String username) {
        Instant now = clock.instant();
        if (failures.size() >= MAX_TRACKED) {
            failures.values().removeIf(f -> f.first().plus(properties.lockout()).isBefore(now));
        }
        failures.compute(key(username), (k, f) -> {
            Failures current = f == null || f.first().plus(properties.lockout()).isBefore(now) && (f.lockedUntil() == null
                    || f.lockedUntil().isBefore(now)) ? new Failures(0, now, null) : f;
            int count = current.count() + 1;
            Instant lockedUntil = count >= properties.maxFailedLogins() ? now.plus(properties.lockout()) : current.lockedUntil();
            return new Failures(count, current.first(), lockedUntil);
        });
    }

    void succeeded(String username) {
        failures.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
