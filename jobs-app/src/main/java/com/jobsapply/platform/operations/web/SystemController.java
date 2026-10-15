package com.jobsapply.platform.operations.web;

import com.jobsapply.platform.operations.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System (admin)", description = "Schedules, retention and delivery backlog")
@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
class SystemController {

    private final SystemService system;

    @GetMapping
    SystemService.SystemView view() {
        return system.view();
    }

    @Operation(summary = "Run the scheduled synchronization now (skipped if one is already running)")
    @PostMapping("/sync")
    SystemService.JobResult syncNow() {
        return system.syncNow();
    }

    @Operation(summary = "Apply the retention rules now")
    @PostMapping("/retention")
    SystemService.JobResult retentionNow() {
        return system.retentionNow();
    }
}
