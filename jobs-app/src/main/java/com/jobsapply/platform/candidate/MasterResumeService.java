package com.jobsapply.platform.candidate;

import com.jobsapply.platform.candidate.internal.CandidateProfile;
import com.jobsapply.platform.candidate.internal.CandidateProfileRepository;
import com.jobsapply.platform.candidate.internal.MasterResume;
import com.jobsapply.platform.candidate.internal.MasterResumeRepository;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.shared.storage.DocumentStore;
import com.jobsapply.platform.shared.tenancy.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The candidate's master resume: stored exactly as uploaded (encrypted at rest) and never modified. Generated resumes
 * are separate documents; each upload creates a new immutable record, the latest being current.
 */
@Service
@RequiredArgsConstructor
public class MasterResumeService {

    /** Accepted types with their leading "magic" bytes, so the declared type must match the content. */
    private static final Map<String, byte[]> SIGNATURES = Map.of(
            "application/pdf", new byte[] {'%', 'P', 'D', 'F'},
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {'P', 'K', 3, 4},
            "text/plain", new byte[0],
            "text/markdown", new byte[0]);

    public record MasterResumeView(UUID id, String fileName, String mediaType, long sizeBytes, String sha256, Instant uploadedAt) {}

    public record Content(MasterResumeView meta, byte[] bytes) {}

    private final CandidateProfileRepository profiles;
    private final MasterResumeRepository resumes;
    private final DocumentStore store;
    private final Clock clock;

    @Transactional
    public MasterResumeView upload(String profileKey, String fileName, String mediaType, byte[] bytes) {
        UUID owner = UserContext.requireUserId();
        CandidateProfile profile = profile(profileKey);
        byte[] signature = SIGNATURES.get(mediaType);
        if (signature == null) {
            throw new DomainRuleViolationException("Unsupported resume type " + mediaType + "; use PDF, DOCX, plain text or Markdown");
        }
        if (bytes.length == 0 || !startsWith(bytes, signature)) {
            throw new DomainRuleViolationException("The file content does not match " + mediaType);
        }
        String key = store.put(owner, bytes);
        try {
            MasterResume saved = resumes.saveAndFlush(MasterResume.builder()
                    .candidateProfileId(profile.getId())
                    .fileName(safeName(fileName))
                    .mediaType(mediaType)
                    .sizeBytes(bytes.length)
                    .sha256(JsonCodec.sha256Hex(bytes))
                    .storageKey(key)
                    .uploadedAt(Instant.now(clock))
                    .build());
            return view(saved);
        } catch (RuntimeException e) {
            store.deleteQuietly(owner, key);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<MasterResumeView> list(String profileKey) {
        return resumes.findByCandidateProfileIdOrderByUploadedAtDesc(profile(profileKey).getId()).stream().map(MasterResumeService::view).toList();
    }

    @Transactional(readOnly = true)
    public Content content(String profileKey, UUID id) {
        UUID owner = UserContext.requireUserId();
        MasterResume resume = resumes.findById(id).filter(r -> r.getCandidateProfileId().equals(profile(profileKey).getId()))
                .orElseThrow(() -> new NotFoundException("Master resume", id));
        return new Content(view(resume), store.get(owner, resume.getStorageKey()));
    }

    private CandidateProfile profile(String key) {
        UserContext.requireUserId();
        return profiles.findByKey(key).orElseThrow(() -> new NotFoundException("Candidate profile", key));
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Display name only (the stored file name is generated); strips any path and control characters. */
    static String safeName(String fileName) {
        String name = fileName == null ? "resume" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        return name.isEmpty() ? "resume" : name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static MasterResumeView view(MasterResume r) {
        return new MasterResumeView(r.getId(), r.getFileName(), r.getMediaType(), r.getSizeBytes(), r.getSha256(), r.getUploadedAt());
    }
}
