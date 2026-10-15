package com.jobsapply.platform.identity;

import java.util.UUID;

/** @param canSignIn whether a password has been set */
public record UserSummary(UUID id, String username, String displayName, String email, UserStatus status, UserRole role,
        boolean canSignIn) {

    @Override
    public String toString() {
        return "UserSummary[id=" + id + ", username=" + username + "]";
    }
}
