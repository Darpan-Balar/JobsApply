package com.jobsapply.platform.candidate;

import com.jobsapply.platform.candidate.internal.CandidateProfile;
import com.jobsapply.platform.candidate.internal.CandidateProfileRepository;
import com.jobsapply.platform.candidate.internal.CandidateProfileVersion;
import com.jobsapply.platform.candidate.internal.CandidateProfileVersionRepository;
import com.jobsapply.platform.candidate.internal.CandidateSnapshotValidator;
import com.jobsapply.platform.shared.crypto.PersonalDataCipher;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateFact;
import com.jobsapply.spi.profile.CandidateSnapshot;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Public API of the candidate module. All operations act on the current user's profiles only; the owner is taken
 * from the bound {@link UserContext}, never from the caller. Snapshots are encrypted at rest.
 */
@Service
@Validated
@RequiredArgsConstructor
public class CandidateProfileService {

    public static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private final CandidateProfileRepository profiles;
    private final CandidateProfileVersionRepository versions;
    private final CandidateSnapshotValidator validator;
    private final PersonalDataCipher cipher;
    private final JsonCodec json;

    /** A version together with its decrypted snapshot. */
    public record CurrentVersion(UUID profileId, UUID versionId, int versionNo, CandidateSnapshot snapshot) {}

    @Transactional
    public CandidateProfileRef createProfile(String key, String displayName) {
        UserContext.requireUserId();
        if (profiles.findByKey(key).isPresent()) {
            throw new DomainRuleViolationException("Candidate profile already exists: " + key);
        }
        CandidateProfile profile = profiles.save(CandidateProfile.builder().key(key).displayName(displayName).build());
        return ref(profile);
    }

    /** Cross-field problems (fact ids only, never content). Empty when valid. */
    public List<String> validate(CandidateSnapshot snapshot) {
        return validator.validate(snapshot);
    }

    /**
     * Records a new immutable version and makes it current. Content identical to the current version is a no-op that
     * returns the current version.
     */
    @Transactional
    public CandidateProfileVersionRef addVersion(UUID profileId, @Valid CandidateSnapshot snapshot, String note) {
        UUID owner = UserContext.requireUserId();
        List<String> problems = validator.validate(snapshot);
        if (!problems.isEmpty()) {
            throw new DomainRuleViolationException("Invalid candidate snapshot: " + String.join("; ", problems));
        }
        CandidateProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Candidate profile", profileId));

        String plaintext = json.write(snapshot);
        String fingerprint = cipher.fingerprint(plaintext);
        if (profile.getCurrentVersionId() != null) {
            CandidateProfileVersion current = versions.findById(profile.getCurrentVersionId()).orElseThrow();
            if (current.getContentHash().equals(fingerprint)) {
                return toRef(current);
            }
        }
        int nextVersionNo = versions.findMaxVersionNo(profileId).orElse(0) + 1;
        CandidateProfileVersion version = versions.save(CandidateProfileVersion.create(profileId, nextVersionNo,
                SNAPSHOT_SCHEMA_VERSION, id -> cipher.encrypt(plaintext, aad(id, owner)), fingerprint, note));
        profile.makeCurrent(version.getId());
        return toRef(version);
    }

    /** Creates the profile if needed and records the snapshot as a new version (idempotent). */
    @Transactional
    public CandidateImportOutcome importProfile(String key, String displayName, @Valid CandidateSnapshot snapshot, String note) {
        UserContext.requireUserId();
        Optional<CandidateProfile> existing = profiles.findByKey(key);
        UUID profileId = existing.map(CandidateProfile::getId).orElseGet(() -> createProfile(key, displayName).id());
        UUID before = existing.map(CandidateProfile::getCurrentVersionId).orElse(null);
        CandidateProfileVersionRef after = addVersion(profileId, snapshot, note);
        if (existing.isEmpty()) {
            return CandidateImportOutcome.CREATED;
        }
        return after.versionId().equals(before) ? CandidateImportOutcome.UNCHANGED : CandidateImportOutcome.NEW_VERSION;
    }

    /**
     * The candidate confirms facts: the listed (or all) UNVERIFIED facts become VERIFIED in a new version.
     * Superseded facts cannot be verified.
     */
    @Transactional
    public CandidateProfileVersionRef verifyFacts(String key, Set<String> factIds, boolean all) {
        CurrentVersion current = current(key).orElseThrow(() -> new NotFoundException("Candidate profile version", key));
        Map<String, CandidateFact> facts = current.snapshot().facts().collect(Collectors.toMap(CandidateFact::id, Function.identity()));
        Set<String> unknown = new TreeSet<>(factIds);
        unknown.removeAll(facts.keySet());
        if (!unknown.isEmpty()) {
            throw new DomainRuleViolationException("Unknown fact id(s): " + String.join(", ", unknown));
        }
        Set<String> superseded = factIds.stream().filter(id -> facts.get(id).verification() == VerificationStatus.SUPERSEDED)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!superseded.isEmpty()) {
            throw new DomainRuleViolationException("Superseded facts cannot be verified: " + String.join(", ", superseded));
        }
        CandidateSnapshot verified = CandidateSnapshots.withVerification(current.snapshot(),
                f -> f.verification() == VerificationStatus.UNVERIFIED && (all || factIds.contains(f.id())), VerificationStatus.VERIFIED);
        long count = verified.facts().filter(f -> f.verification() == VerificationStatus.VERIFIED).count()
                - current.snapshot().facts().filter(f -> f.verification() == VerificationStatus.VERIFIED).count();
        return addVersion(current.profileId(), verified, "verified " + count + " fact(s)");
    }

    @Transactional(readOnly = true)
    public List<CandidateProfileRef> listProfiles() {
        UserContext.requireUserId();
        return profiles.findAllByOrderByKey().stream().map(CandidateProfileService::ref).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CurrentVersion> current(String key) {
        UserContext.requireUserId();
        return profiles.findByKey(key)
                .filter(p -> p.getCurrentVersionId() != null)
                .flatMap(p -> versions.findById(p.getCurrentVersionId()))
                .map(v -> new CurrentVersion(v.getCandidateProfileId(), v.getId(), v.getVersionNo(), readSnapshot(v)));
    }

    @Transactional(readOnly = true)
    public CandidateProfileDetail detail(String key) {
        UserContext.requireUserId();
        CandidateProfile profile = profiles.findByKey(key).orElseThrow(() -> new NotFoundException("Candidate profile", key));
        if (profile.getCurrentVersionId() == null) {
            return new CandidateProfileDetail(ref(profile), null, null, Map.of());
        }
        CandidateProfileVersion version = versions.findById(profile.getCurrentVersionId()).orElseThrow();
        CandidateSnapshot snapshot = readSnapshot(version);
        return new CandidateProfileDetail(ref(profile), toRef(version), snapshot,
                snapshot.facts().collect(Collectors.groupingBy(CandidateFact::verification, Collectors.counting())));
    }

    @Transactional(readOnly = true)
    public List<CandidateProfileVersionRef> versions(String key) {
        UserContext.requireUserId();
        CandidateProfile profile = profiles.findByKey(key).orElseThrow(() -> new NotFoundException("Candidate profile", key));
        return versions.findByCandidateProfileIdOrderByVersionNoDesc(profile.getId()).stream().map(CandidateProfileService::toRef).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CandidateProfileVersionView> findVersion(UUID versionId) {
        UserContext.requireUserId();
        return versions.findById(versionId).map(v -> new CandidateProfileVersionView(toRef(v), readSnapshot(v)));
    }

    /** Profile key and version number for display, keyed by version id. */
    @Transactional(readOnly = true)
    public Map<UUID, String> versionLabels(Set<UUID> versionIds) {
        UserContext.requireUserId();
        Map<UUID, String> keys = profiles.findAllByOrderByKey().stream().collect(Collectors.toMap(CandidateProfile::getId, CandidateProfile::getKey));
        return versions.findAllById(versionIds).stream().collect(Collectors.toMap(CandidateProfileVersion::getId,
                v -> keys.get(v.getCandidateProfileId()) + " v" + v.getVersionNo()));
    }

    private CandidateSnapshot readSnapshot(CandidateProfileVersion version) {
        String stored = version.getSnapshotJson();
        String plaintext = stored.contains("\"alg\"") ? cipher.decrypt(stored, aad(version.getId(), version.getOwnerUserId())) : stored;
        return json.read(plaintext, CandidateSnapshot.class);
    }

    /** Binds the ciphertext to this row and owner: copying it elsewhere makes decryption fail. */
    private static String aad(UUID versionId, UUID ownerId) {
        return "candidate_profile_version|" + versionId + "|" + ownerId;
    }

    private static CandidateProfileRef ref(CandidateProfile p) {
        return new CandidateProfileRef(p.getId(), p.getKey(), p.getDisplayName());
    }

    private static CandidateProfileVersionRef toRef(CandidateProfileVersion version) {
        return new CandidateProfileVersionRef(
                version.getCandidateProfileId(), version.getId(), version.getVersionNo(), version.getContentHash());
    }
}
