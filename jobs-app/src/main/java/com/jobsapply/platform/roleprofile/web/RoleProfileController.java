package com.jobsapply.platform.roleprofile.web;

import com.jobsapply.platform.roleprofile.RoleProfileImportResult;
import com.jobsapply.platform.roleprofile.RoleProfileService;
import com.jobsapply.platform.roleprofile.VisibleRoleProfile;
import com.jobsapply.platform.shared.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Role profiles", description = "Shared templates plus your own private profiles")
@RestController
@RequiredArgsConstructor
class RoleProfileController {

    private final RoleProfileService roleProfiles;

    @Operation(summary = "Role profiles visible to you", description = "Your own profiles and the shared templates.")
    @GetMapping("/api/v1/role-profiles")
    List<VisibleRoleProfile> list() {
        return roleProfiles.listVisible();
    }

    @Operation(summary = "A role profile", description = "Your own profile with this key if you have one, otherwise the template.")
    @GetMapping("/api/v1/role-profiles/{key}")
    VisibleRoleProfile get(@PathVariable String key) {
        return roleProfiles.findVisible(key).orElseThrow(() -> new NotFoundException("Role profile", key));
    }

    @Operation(summary = "Copy a template into your own profiles, to tune it")
    @PostMapping("/api/v1/me/role-profiles/{key}/fork")
    RoleProfileImportResult fork(@PathVariable String key) {
        return roleProfiles.forkTemplate(key);
    }
}
