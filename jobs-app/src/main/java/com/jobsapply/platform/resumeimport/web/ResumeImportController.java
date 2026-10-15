package com.jobsapply.platform.resumeimport.web;

import com.jobsapply.platform.resumeimport.ProfileDraft;
import com.jobsapply.platform.resumeimport.ResumeImportService;
import com.jobsapply.platform.shared.web.RequestBodies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile from resume", description = "Propose an unconfirmed profile from a resume file (nothing is saved)")
@RestController
@RequestMapping("/api/v1/me/candidate-profiles")
@RequiredArgsConstructor
class ResumeImportController {

    private final ResumeImportService imports;

    @GetMapping("/extractors")
    List<ResumeImportService.ExtractorOption> extractors() {
        return imports.extractors();
    }

    @Operation(summary = "Read a resume (raw PDF, DOCX or text body) into a draft profile",
            description = "Nothing is stored. Every fact is unconfirmed; review and save it with PUT /me/candidate-profiles/{key}. "
                    + "An extractor that sends text externally receives it with e-mail, phone, links and your name masked.")
    @PostMapping("/drafts")
    ResponseEntity<ProfileDraft> draft(@RequestParam(defaultValue = "resume") String fileName, @RequestParam(required = false) String extractor,
            HttpServletRequest request) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(imports.draft(RequestBodies.readBytes(request), request.getContentType(), fileName, extractor));
    }
}
