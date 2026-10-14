package com.jobsapply.platform.discovery.web;

import com.jobsapply.platform.discovery.AvailabilityService;
import com.jobsapply.platform.discovery.SyncReport;
import com.jobsapply.platform.discovery.SyncRunQueryService;
import com.jobsapply.platform.discovery.SyncRunView;
import com.jobsapply.platform.discovery.SyncService;
import com.jobsapply.platform.discovery.SyncTrigger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Discovery", description = "Synchronization with job sources and availability rechecks")
@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class DiscoveryController {

    private final SyncService sync;
    private final SyncRunQueryService runs;
    private final AvailabilityService availability;

    @Operation(summary = "Synchronize now", description = "All active bindings, or narrowed by source and/or employer. "
            + "Runs synchronously; dryRun reports what would change without changing anything.")
    @PostMapping("/sync")
    List<SyncReport> sync(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String employer,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return sync.sync(source, employer, SyncTrigger.MANUAL, dryRun);
    }

    @GetMapping("/sync-runs")
    List<SyncRunView> recent(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String employer,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return runs.recent(source, employer, limit);
    }

    @GetMapping("/sync-runs/{id}")
    SyncRunView run(@PathVariable UUID id) {
        return runs.get(id);
    }

    @Operation(summary = "Recheck a posting's availability at its sources", description = "Required before preparing an application.")
    @PostMapping("/postings/{id}/availability-check")
    AvailabilityService.AvailabilityReport recheck(@PathVariable UUID id) {
        return availability.recheck(id);
    }
}
