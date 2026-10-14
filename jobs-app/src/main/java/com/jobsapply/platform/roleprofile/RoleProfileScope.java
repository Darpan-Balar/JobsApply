package com.jobsapply.platform.roleprofile;

public enum RoleProfileScope {
    /** Shared, read-only template visible to every user (e.g. shipped sample profiles). */
    SYSTEM_TEMPLATE,
    /** Private to the user bound in the current {@code UserContext}. */
    CURRENT_USER
}
