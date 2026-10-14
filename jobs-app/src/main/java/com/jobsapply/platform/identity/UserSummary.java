package com.jobsapply.platform.identity;

import java.util.UUID;

public record UserSummary(UUID id, String username, String displayName, String email, UserStatus status) {

    @Override
    public String toString() {
        return "UserSummary[id=" + id + ", username=" + username + "]";
    }
}
