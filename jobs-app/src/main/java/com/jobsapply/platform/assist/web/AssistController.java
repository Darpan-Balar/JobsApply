package com.jobsapply.platform.assist.web;

import com.jobsapply.platform.assist.AssistService;
import com.jobsapply.platform.assist.AssistView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Browser assistance", description = "Fill the approved application in a visible browser window; you press Submit")
@Validated
@RestController
@RequestMapping("/api/v1/me/applications/{id}")
@RequiredArgsConstructor
class AssistController {

    private final AssistService assist;

    @Operation(summary = "Use the approval and open the form in a browser window",
            description = "Requires an Idempotency-Key. Fills only values that are certain and come from your approved package; "
                    + "pauses on sign-in, CAPTCHA challenges and one-time codes. Never presses Submit.")
    @PostMapping("/assist")
    AssistView start(@PathVariable UUID id, @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}") String key) {
        return assist.start(id, key);
    }

    @GetMapping("/assist")
    AssistView view(@PathVariable UUID id) {
        return assist.view(id);
    }

    @Operation(summary = "Continue after you resolved a pause (inspect and fill again; never overwrites what you typed)")
    @PostMapping("/assist/refresh")
    AssistView refresh(@PathVariable UUID id) {
        return assist.refresh(id);
    }

    @Operation(summary = "After you pressed Submit: look for the confirmation and record it as evidence")
    @PostMapping("/assist/confirmation")
    AssistView confirm(@PathVariable UUID id, @RequestParam(defaultValue = "30") @Min(1) @Max(600) int waitSeconds) {
        return assist.confirm(id, Duration.ofSeconds(waitSeconds));
    }

    @Operation(summary = "Close the window (the attempt stays in progress until you report its outcome)")
    @DeleteMapping("/assist")
    AssistView close(@PathVariable UUID id) {
        return assist.close(id);
    }

    @GetMapping("/attempts/{attemptId}/screenshot")
    ResponseEntity<byte[]> screenshot(@PathVariable UUID id, @PathVariable UUID attemptId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(assist.screenshot(id, attemptId));
    }
}
