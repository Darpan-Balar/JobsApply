package com.jobsapply.platform.audit.web;

import com.jobsapply.platform.audit.AuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My audit trail", description = "Append-only record of what happened to your data")
@Validated
@RestController
@RequiredArgsConstructor
class AuditController {

    private final AuditService audit;

    @GetMapping("/api/v1/me/audit")
    List<AuditService.AuditView> mine(@RequestParam(required = false) UUID aggregateId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return audit.mine(aggregateId, limit);
    }
}
