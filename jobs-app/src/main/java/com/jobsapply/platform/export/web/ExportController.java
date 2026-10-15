package com.jobsapply.platform.export.web;

import com.jobsapply.platform.export.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My data export", description = "Everything personal the platform holds about you, as JSON")
@RestController
@RequiredArgsConstructor
class ExportController {

    private final ExportService exports;
    private final Clock clock;

    @Operation(summary = "Download all your data (profiles, applications, documents list, tracking, preferences, audit)")
    @GetMapping("/api/v1/me/export")
    ResponseEntity<ExportService.Export> export() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("jobs-export-" + LocalDate.now(clock) + ".json").build().toString())
                .body(exports.export());
    }
}
