package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.UserRole;
import java.util.UUID;

/**
 * What the dashboard needs to know about the current session.
 *
 * @param user the signed-in user, or {@code null}
 * @param setupRequired no account can sign in yet: offer the one-time setup
 * @param setupAllowedHere setup is only offered to requests from this machine
 */
public record SessionView(Me user, boolean setupRequired, boolean setupAllowedHere) {

    public record Me(UUID id, String username, String displayName, UserRole role) {}
}
