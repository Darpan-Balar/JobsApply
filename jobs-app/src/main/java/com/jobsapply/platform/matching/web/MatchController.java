package com.jobsapply.platform.matching.web;

import com.jobsapply.platform.matching.MatchRunReport;
import com.jobsapply.platform.matching.MatchView;
import com.jobsapply.platform.matching.MatchingService;
import com.jobsapply.platform.shared.model.PageResult;
import com.jobsapply.spi.common.Eligibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My matches", description = "Explainable matches of postings against your candidate and role profiles")
@Validated
@RestController
@RequestMapping("/api/v1/me/matches")
@RequiredArgsConstructor
class MatchController {

    private final MatchingService matching;

    record Evaluate(@NotNull UUID postingId, @NotBlank String candidateProfile, @NotBlank String roleProfile) {}

    record Run(@NotBlank String candidateProfile, @NotBlank String roleProfile) {}

    @Operation(summary = "Evaluate one posting")
    @PostMapping("/evaluate")
    MatchingService.Evaluation evaluate(@Valid @RequestBody Evaluate body) {
        return matching.evaluate(body.postingId(), body.candidateProfile(), body.roleProfile());
    }

    @Operation(summary = "Evaluate all open postings", description = "Employers you IGNORE are skipped. Idempotent: unchanged results are reported as such.")
    @PostMapping("/run")
    MatchRunReport run(@Valid @RequestBody Run body) {
        return matching.run(body.candidateProfile(), body.roleProfile());
    }

    @Operation(summary = "Matches for your profiles' current versions, best first")
    @GetMapping
    PageResult<MatchView> list(
            @RequestParam String candidateProfile,
            @RequestParam String roleProfile,
            @RequestParam(required = false) Eligibility eligibility,
            @RequestParam(required = false) BigDecimal minScore,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return matching.list(candidateProfile, roleProfile, eligibility, minScore, page, size);
    }

    @GetMapping("/{id}")
    MatchView get(@PathVariable UUID id) {
        return matching.get(id);
    }
}
