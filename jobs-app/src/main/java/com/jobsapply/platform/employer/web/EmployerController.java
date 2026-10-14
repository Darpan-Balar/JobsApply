package com.jobsapply.platform.employer.web;

import com.jobsapply.platform.employer.BindingSpec;
import com.jobsapply.platform.employer.BindingView;
import com.jobsapply.platform.employer.EmployerQuery;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.EmployerSort;
import com.jobsapply.platform.employer.EmployerView;
import com.jobsapply.platform.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employers", description = "Shared employer registry and employer-to-source bindings")
@Validated
@RestController
@RequestMapping("/api/v1/employers")
@RequiredArgsConstructor
class EmployerController {

    private final EmployerService employers;

    @Operation(summary = "Search employers", description = "Filters combine with AND. Archived employers are excluded unless includeArchived=true.")
    @GetMapping
    PageResult<EmployerView> search(
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minPriority,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String adapter,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "PRIORITY") EmployerSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        EmployerQuery query = EmployerQuery.builder()
                .tag(tag).industry(industry).minPriority(minPriority).sourceKey(source).adapterId(adapter)
                .enabled(enabled).includeArchived(includeArchived).text(q)
                .build();
        return employers.search(query, sort, page, size);
    }

    @GetMapping("/{key}")
    EmployerView get(@PathVariable String key) {
        return employers.get(key);
    }

    @PostMapping
    ResponseEntity<EmployerView> create(@Valid @RequestBody EmployerRequests.CreateEmployer body) {
        EmployerView created = employers.create(body.toSpec(), body.bindingSpecs());
        return ResponseEntity.created(URI.create("/api/v1/employers/" + created.key())).body(created);
    }

    @Operation(summary = "Replace an employer's details", description = "Requires the version last read; returns 409 if it changed meanwhile.")
    @PutMapping("/{key}")
    EmployerView update(@PathVariable String key, @Valid @RequestBody EmployerRequests.UpdateEmployer body) {
        return employers.update(key, body.toSpec(key), body.version());
    }

    @Operation(summary = "Archive an employer", description = "Soft delete: hidden from search and discovery, history retained.")
    @PostMapping("/{key}/archive")
    EmployerView archive(@PathVariable String key) {
        return employers.archive(key);
    }

    @PostMapping("/{key}/unarchive")
    EmployerView unarchive(@PathVariable String key) {
        return employers.unarchive(key);
    }

    @Operation(summary = "Create or reconfigure a binding of the employer to a job source")
    @PutMapping("/{key}/bindings/{sourceKey}/{externalRef}")
    BindingView putBinding(
            @PathVariable String key, @PathVariable String sourceKey, @PathVariable String externalRef,
            @RequestBody(required = false) EmployerRequests.PutBinding body) {
        boolean enabledFlag = body == null || body.enabled() == null || body.enabled();
        int priority = body == null || body.priority() == null ? 50 : body.priority();
        return employers.putBinding(key, new BindingSpec(sourceKey, externalRef, enabledFlag, priority));
    }

    @Operation(summary = "Disable a binding", description = "Bindings are never deleted because discovered listings reference them.")
    @DeleteMapping("/{key}/bindings/{sourceKey}/{externalRef}")
    ResponseEntity<Void> disableBinding(@PathVariable String key, @PathVariable String sourceKey, @PathVariable String externalRef) {
        employers.disableBinding(key, sourceKey, externalRef);
        return ResponseEntity.noContent().build();
    }
}
