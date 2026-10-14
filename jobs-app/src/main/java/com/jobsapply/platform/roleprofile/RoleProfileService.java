package com.jobsapply.platform.roleprofile;

import com.jobsapply.platform.catalog.CatalogService;
import com.jobsapply.platform.roleprofile.internal.RoleProfile;
import com.jobsapply.platform.roleprofile.internal.RoleProfileRepository;
import com.jobsapply.platform.roleprofile.internal.RoleProfileVersion;
import com.jobsapply.platform.roleprofile.internal.RoleProfileVersionRepository;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the role-profile module. */
@Service
@RequiredArgsConstructor
public class RoleProfileService {

    private final RoleProfileRepository profiles;
    private final RoleProfileVersionRepository versions;
    private final CatalogService catalog;
    private final JsonCodec json;

    /**
     * Idempotently imports a (validated) definition. Identical content never creates a new version; content equal to
     * an older version re-activates it.
     */
    @Transactional
    public RoleProfileImportResult importDefinition(
            RoleProfileDefinition definition, int schemaVersion, RoleProfileScope scope) {
        UUID owner = scope == RoleProfileScope.CURRENT_USER ? UserContext.requireUserId() : null;
        UUID jobFamilyId = catalog.findJobFamilyId(definition.jobFamily())
                .orElseThrow(() -> new DomainRuleViolationException("Unknown job family: " + definition.jobFamily()));
        String definitionJson = json.write(definition);
        String contentHash = JsonCodec.sha256Hex(definitionJson);

        Optional<RoleProfile> existing = owner == null
                ? profiles.findTemplate(definition.key())
                : profiles.findOwned(owner, definition.key());

        if (existing.isEmpty()) {
            RoleProfile profile = profiles.save(RoleProfile.builder()
                    .ownerUserId(owner)
                    .jobFamilyId(jobFamilyId)
                    .key(definition.key())
                    .displayName(definition.displayName())
                    .build());
            RoleProfileVersion version = versions.save(
                    RoleProfileVersion.of(profile.getId(), 1, schemaVersion, definitionJson, contentHash));
            profile.activate(version.getId());
            return result(definition.key(), RoleProfileImportOutcome.CREATED, version);
        }

        RoleProfile profile = existing.get();
        profile.describe(definition.displayName(), jobFamilyId);
        Optional<RoleProfileVersion> sameContent = versions.findByRoleProfileIdAndContentHash(profile.getId(), contentHash);
        if (sameContent.isPresent()) {
            RoleProfileVersion version = sameContent.get();
            boolean wasActive = version.getId().equals(profile.getActiveVersionId());
            profile.activate(version.getId());
            return result(definition.key(),
                    wasActive ? RoleProfileImportOutcome.UNCHANGED : RoleProfileImportOutcome.REACTIVATED, version);
        }

        int nextVersionNo = versions.findMaxVersionNo(profile.getId()).orElse(0) + 1;
        RoleProfileVersion version = versions.save(
                RoleProfileVersion.of(profile.getId(), nextVersionNo, schemaVersion, definitionJson, contentHash));
        profile.activate(version.getId());
        return result(definition.key(), RoleProfileImportOutcome.NEW_VERSION, version);
    }

    /** Copies a system template into the current user's private profiles (same key), to tune it. */
    @Transactional
    public RoleProfileImportResult forkTemplate(String key) {
        UUID owner = UserContext.requireUserId();
        if (profiles.findOwned(owner, key).isPresent()) {
            throw new DomainRuleViolationException("You already have a role profile '" + key + "'");
        }
        RoleProfile template = profiles.findTemplate(key).orElseThrow(() -> new NotFoundException("Role profile template", key));
        RoleProfileVersion version = versions.findById(template.getActiveVersionId()).orElseThrow();
        return importDefinition(json.read(version.getDefinitionJson(), RoleProfileDefinition.class), version.getSchemaVersion(),
                RoleProfileScope.CURRENT_USER);
    }

    /**
     * Resolves a key for the current user: the user's own profile if present, otherwise the system template.
     * Other users' profiles are never visible.
     */
    @Transactional(readOnly = true)
    public Optional<VisibleRoleProfile> findVisible(String key) {
        UUID owner = UserContext.requireUserId();
        return profiles.findOwned(owner, key).or(() -> profiles.findTemplate(key)).flatMap(this::toVisible);
    }

    /** All role profiles visible to the current user (own profiles and system templates). */
    @Transactional(readOnly = true)
    public List<VisibleRoleProfile> listVisible() {
        UUID owner = UserContext.requireUserId();
        return profiles.findVisible(owner).stream().map(this::toVisible).flatMap(Optional::stream).toList();
    }

    private Optional<VisibleRoleProfile> toVisible(RoleProfile profile) {
        if (profile.getActiveVersionId() == null) {
            return Optional.empty();
        }
        return versions.findById(profile.getActiveVersionId()).map(version -> new VisibleRoleProfile(
                profile.getKey(),
                profile.getOwnerUserId() == null,
                toRef(version),
                json.read(version.getDefinitionJson(), RoleProfileDefinition.class)));
    }

    private static RoleProfileImportResult result(String key, RoleProfileImportOutcome outcome, RoleProfileVersion version) {
        return new RoleProfileImportResult(key, outcome, toRef(version));
    }

    private static RoleProfileVersionRef toRef(RoleProfileVersion version) {
        return new RoleProfileVersionRef(
                version.getRoleProfileId(), version.getId(), version.getVersionNo(), version.getContentHash());
    }
}
