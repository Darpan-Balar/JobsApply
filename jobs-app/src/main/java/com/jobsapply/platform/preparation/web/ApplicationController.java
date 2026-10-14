package com.jobsapply.platform.preparation.web;

import com.jobsapply.platform.application.ApplicationView;
import com.jobsapply.platform.preparation.PreparationService;
import com.jobsapply.platform.preparation.PreparationViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current user's applications. Responses contain personal data and are never cached. Nothing here submits. */
@Tag(name = "My applications", description = "Prepare, review and edit application packages (no submission in this phase)")
@RestController
@RequestMapping("/api/v1/me/applications")
@RequiredArgsConstructor
class ApplicationController {

    private final PreparationService preparation;

    record Prepare(@NotNull UUID postingId, @NotBlank String candidateProfile, @NotBlank String roleProfile,
            List<@Valid Question> additionalQuestions) {}

    record Question(@NotBlank @Size(max = 2000) String text, boolean required) {}

    record AnswerBody(@Size(max = 10_000) String answer) {}

    record ResumeBody(@NotNull List<PreparationViews.SectionEdit> sections) {}

    record RejectBody(@Size(max = 1000) String reason) {}

    @Operation(summary = "Prepare an application package",
            description = "Rechecks availability, evaluates the match, generates fact-backed documents and answer drafts, and "
                    + "validates that every statement is traceable to your verified facts. Idempotent: unchanged inputs give UNCHANGED.")
    @PostMapping("/prepare")
    PreparationViews.Result prepare(@Valid @RequestBody Prepare body) {
        return preparation.prepare(body.postingId(), body.candidateProfile(), body.roleProfile(),
                body.additionalQuestions() == null ? List.of()
                        : body.additionalQuestions().stream().map(q -> new PreparationService.QuestionInput(q.text(), q.required())).toList());
    }

    @GetMapping
    List<PreparationViews.ApplicationSummary> list() {
        return preparation.list();
    }

    @GetMapping("/{id}")
    ResponseEntity<PreparationViews.ApplicationDetail> get(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(preparation.detail(id));
    }

    @GetMapping("/{id}/packages")
    List<PreparationViews.PackageVersion> versions(@PathVariable UUID id) {
        return preparation.versions(id);
    }

    @GetMapping("/{id}/documents/{documentId}")
    ResponseEntity<byte[]> document(@PathVariable UUID id, @PathVariable UUID documentId) {
        PreparationViews.Download download = preparation.document(id, documentId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.bytes());
    }

    @Operation(summary = "Answer a question yourself (blank clears it)", description = "Creates a new package version.")
    @PutMapping("/{id}/answers/{questionId}")
    PreparationViews.Result answer(@PathVariable UUID id, @PathVariable String questionId, @Valid @RequestBody AnswerBody body) {
        return preparation.answer(id, questionId, body.answer());
    }

    @Operation(summary = "Edit the resume", description = "Each statement must cite your verified facts (factIds) or be your own "
            + "approved text (userApproved=true). Untraceable statements are rejected. Creates a new package version.")
    @PutMapping("/{id}/resume")
    PreparationViews.Result editResume(@PathVariable UUID id, @Valid @RequestBody ResumeBody body) {
        return preparation.editResume(id, body.sections());
    }

    @PostMapping("/{id}/reject")
    ApplicationView reject(@PathVariable UUID id, @Valid @RequestBody(required = false) RejectBody body) {
        return preparation.reject(id, body == null ? null : body.reason());
    }
}
