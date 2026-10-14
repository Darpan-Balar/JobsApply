package com.jobsapply.platform.candidate.web;

import com.jobsapply.platform.candidate.CandidateImportOutcome;
import com.jobsapply.platform.candidate.CandidateProfileDetail;
import com.jobsapply.platform.candidate.CandidateProfileRef;
import com.jobsapply.platform.candidate.CandidateProfileService;
import com.jobsapply.platform.candidate.CandidateProfileVersionRef;
import com.jobsapply.spi.profile.CandidateSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current user's private candidate profiles. Responses contain personal data and are never cached. */
@Tag(name = "My candidate profiles", description = "Versioned facts about you; private to you")
@RestController
@RequestMapping("/api/v1/me/candidate-profiles")
@RequiredArgsConstructor
class CandidateProfileController {

    private final CandidateProfileService candidates;

    record PutProfile(@NotBlank String displayName, @NotNull CandidateSnapshot snapshot, String note) {}

    record Verify(Set<String> factIds, Boolean all) {}

    record PutResult(CandidateImportOutcome outcome, CandidateProfileVersionRef version) {}

    @GetMapping
    List<CandidateProfileRef> list() {
        return candidates.listProfiles();
    }

    @GetMapping("/{key}")
    ResponseEntity<CandidateProfileDetail> get(@PathVariable String key) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(candidates.detail(key));
    }

    @GetMapping("/{key}/versions")
    List<CandidateProfileVersionRef> versions(@PathVariable String key) {
        return candidates.versions(key);
    }

    @Operation(summary = "Save a new version of a profile (created if missing)",
            description = "Facts keep the verification status you send: this is your own statement. Identical content is a no-op.")
    @PutMapping("/{key}")
    PutResult put(@PathVariable String key, @RequestBody @Valid PutProfile body) {
        CandidateImportOutcome outcome = candidates.importProfile(key, body.displayName(), body.snapshot(),
                body.note() == null ? "edited" : body.note());
        return new PutResult(outcome, candidates.versions(key).getFirst());
    }

    @Operation(summary = "Confirm facts", description = "Marks the listed (or all) UNVERIFIED facts as VERIFIED in a new version. "
            + "Only verified facts count in matching and generated documents.")
    @PostMapping("/{key}/verification")
    CandidateProfileVersionRef verify(@PathVariable String key, @RequestBody Verify body) {
        boolean all = Boolean.TRUE.equals(body.all());
        return candidates.verifyFacts(key, body.factIds() == null ? Set.of() : body.factIds(), all);
    }
}
