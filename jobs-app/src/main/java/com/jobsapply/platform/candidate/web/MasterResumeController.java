package com.jobsapply.platform.candidate.web;

import com.jobsapply.platform.candidate.MasterResumeService;
import com.jobsapply.platform.shared.web.RequestBodies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My master resume", description = "Your own resume file, stored exactly as uploaded (encrypted)")
@RestController
@RequestMapping("/api/v1/me/candidate-profiles/{key}/master-resumes")
@RequiredArgsConstructor
class MasterResumeController {

    private final MasterResumeService resumes;

    @Operation(summary = "Upload your master resume", description = "Raw body with Content-Type application/pdf, the DOCX type, "
            + "text/plain or text/markdown. Max 5 MB. It is never modified; generated resumes are separate documents.")
    @PostMapping
    MasterResumeService.MasterResumeView upload(@PathVariable String key, @RequestParam(defaultValue = "resume") String fileName,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, HttpServletRequest request) throws IOException {
        String mediaType = MediaType.parseMediaType(contentType).getType() + "/" + MediaType.parseMediaType(contentType).getSubtype();
        return resumes.upload(key, fileName, mediaType, RequestBodies.readBytes(request));
    }

    @GetMapping
    List<MasterResumeService.MasterResumeView> list(@PathVariable String key) {
        return resumes.list(key);
    }

    @GetMapping("/{id}/content")
    ResponseEntity<byte[]> content(@PathVariable String key, @PathVariable UUID id) {
        MasterResumeService.Content content = resumes.content(key, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.meta().mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.meta().fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }
}
