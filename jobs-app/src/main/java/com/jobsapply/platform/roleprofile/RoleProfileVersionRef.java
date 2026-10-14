package com.jobsapply.platform.roleprofile;

import java.util.UUID;

public record RoleProfileVersionRef(UUID roleProfileId, UUID versionId, int versionNo, String contentHash) {}
