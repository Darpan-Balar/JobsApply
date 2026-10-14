package com.jobsapply.platform.candidate;

import com.jobsapply.platform.candidate.internal.CandidateProfile;
import com.jobsapply.platform.candidate.internal.CandidateProfileRepository;
import com.jobsapply.platform.candidate.internal.CandidateProfileVersion;
import com.jobsapply.platform.candidate.internal.CandidateProfileVersionRepository;
import com.jobsapply.platform.candidate.internal.CandidateSnapshotValidator;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.spi.profile.CandidateSnapshot;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Public API of the candidate module. All operations act on the current user's profiles only; the owner is taken
 * from the bound {@link UserContext}, never from the caller.
 */
@Service
@Validated
@RequiredArgsConstructor
public class CandidateProfileService {

    public static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private final CandidateProfileRepository profiles;
    private final CandidateProfileVersionRepository versions;
    private final CandidateSnapshotValidator validator;
    private final JsonCodec json;

    @Transactional
    public CandidateProfileRef createProfile(String key, String displayName) {
        UserContext.requireUserId();
        if (profiles.findByKey(key).isPresent()) {
            throw new DomainRuleViolationException("Candidate profile already exists: " + key);
        }
        CandidateProfile profile = profiles.save(CandidateProfile.builder().key(key).displayName(displayName).build());
        return new CandidateProfileRef(profile.getId(), profile.getKey(), profile.getDisplayName());
    }

    /**
     * Records a new immutable version and makes it current. Submitting content identical to the current version is a
     * no-op that returns the current version.
     */
    @Transactional
    public CandidateProfileVersionRef addVersion(UUID profileId, @Valid CandidateSnapshot snapshot, String note) {
        UserContext.requireUserId();
        List<String> problems = validator.validate(snapshot);
        if (!problems.isEmpty()) {
            throw new DomainRuleViolationException("Invalid candidate snapshot: " + String.join("; ", problems));
        }
        CandidateProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Candidate profile", profileId));

        String snapshotJson = json.write(snapshot);
        String contentHash = JsonCodec.sha256Hex(snapshotJson);
        if (profile.getCurrentVersionId() != null) {
            CandidateProfileVersion current = versions.findById(profile.getCurrentVersionId()).orElseThrow();
            if (current.getContentHash().equals(contentHash)) {
                return toRef(current);
            }
        }
        int nextVersionNo = versions.findMaxVersionNo(profileId).orElse(0) + 1;
        CandidateProfileVersion version = versions.save(CandidateProfileVersion.of(
                profileId, nextVersionNo, SNAPSHOT_SCHEMA_VERSION, snapshotJson, contentHash, note));
        profile.makeCurrent(version.getId());
        return toRef(version);
    }

    @Transactional(readOnly = true)
    public List<CandidateProfileRef> listProfiles() {
        UserContext.requireUserId();
        return profiles.findAllByOrderByKey().stream()
                .map(p -> new CandidateProfileRef(p.getId(), p.getKey(), p.getDisplayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CandidateProfileVersionView> findVersion(UUID versionId) {
        UserContext.requireUserId();
        return versions.findById(versionId)
                .map(v -> new CandidateProfileVersionView(toRef(v), json.read(v.getSnapshotJson(), CandidateSnapshot.class)));
    }

    private static CandidateProfileVersionRef toRef(CandidateProfileVersion version) {
        return new CandidateProfileVersionRef(
                version.getCandidateProfileId(), version.getId(), version.getVersionNo(), version.getContentHash());
    }
}
