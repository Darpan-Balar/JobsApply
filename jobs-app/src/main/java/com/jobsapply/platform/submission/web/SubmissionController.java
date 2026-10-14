package com.jobsapply.platform.submission.web;

import com.jobsapply.platform.application.ApplicationService.ApprovalView;
import com.jobsapply.platform.application.ApplicationService.AttemptView;
import com.jobsapply.platform.application.ApplicationView;
import com.jobsapply.platform.application.AttemptOutcome;
import com.jobsapply.platform.application.SubmissionEvidence;
import com.jobsapply.platform.submission.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My submissions", description = "Approve one exact package, submit once, record evidence, verify uncertain outcomes")
@RestController
@RequestMapping("/api/v1/me/applications/{id}")
@RequiredArgsConstructor
class SubmissionController {

    private final SubmissionService submissions;

    record Approve(@NotNull UUID packageId) {}

    record Outcome(@NotNull AttemptOutcome outcome, @Valid SubmissionEvidence evidence) {}

    record Verify(@NotNull AttemptOutcome result, @Valid SubmissionEvidence evidence) {}

    @GetMapping("/approval/blockers")
    List<String> blockers(@PathVariable UUID id) {
        return submissions.approvalBlockers(id);
    }

    @Operation(summary = "Approve the current package for one submission",
            description = "Send the id of the package you reviewed; refused if it is no longer current or not complete.")
    @PostMapping("/approval")
    ApprovalView approve(@PathVariable UUID id, @Valid @RequestBody Approve body) {
        return submissions.approve(id, body.packageId());
    }

    @DeleteMapping("/approval")
    ApplicationView revoke(@PathVariable UUID id) {
        return submissions.revoke(id);
    }

    @GetMapping("/approvals")
    List<ApprovalView> approvals(@PathVariable UUID id) {
        return submissions.approvals(id);
    }

    @Operation(summary = "Start the submission (uses the approval)", description = "Requires an Idempotency-Key header; replays return "
            + "the same attempt. You then submit in your browser and report the outcome.")
    @PostMapping("/submission")
    SubmissionService.StartedSubmission start(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String key) {
        return submissions.start(id, key);
    }

    @Operation(summary = "Report how the submission ended", description = "CONFIRMED needs evidence; FAILED needs a description; "
            + "UNCONFIRMED when unsure (blocks any retry until you verify).")
    @PostMapping("/submission/{attemptId}/outcome")
    AttemptView outcome(@PathVariable UUID id, @PathVariable UUID attemptId, @Valid @RequestBody Outcome body) {
        return submissions.reportOutcome(id, attemptId, body.outcome(), body.evidence());
    }

    @Operation(summary = "Resolve an unconfirmed submission", description = "Only you can: CONFIRMED (with evidence) or FAILED.")
    @PostMapping("/verification")
    AttemptView verify(@PathVariable UUID id, @Valid @RequestBody Verify body) {
        return submissions.verify(id, body.result(), body.evidence());
    }

    @GetMapping("/attempts")
    List<AttemptView> attempts(@PathVariable UUID id) {
        return submissions.attempts(id);
    }

    @PostMapping("/withdrawal")
    ApplicationView withdraw(@PathVariable UUID id) {
        return submissions.withdraw(id);
    }
}
