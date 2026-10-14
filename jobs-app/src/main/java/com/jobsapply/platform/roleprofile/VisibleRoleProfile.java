package com.jobsapply.platform.roleprofile;

import com.jobsapply.spi.profile.RoleProfileDefinition;

/** A role profile as seen by the current user, with its active version's definition. */
public record VisibleRoleProfile(
        String key, boolean systemTemplate, RoleProfileVersionRef activeVersion, RoleProfileDefinition definition) {}
