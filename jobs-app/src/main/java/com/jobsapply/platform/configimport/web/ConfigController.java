package com.jobsapply.platform.configimport.web;

import com.jobsapply.platform.configimport.CandidateImportService;
import com.jobsapply.platform.configimport.ConfigImportReport;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.configimport.ConfigProperties;
import com.jobsapply.platform.configimport.EmployerExchangeService;
import com.jobsapply.platform.shared.web.RequestBodies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuration validation/import and employer CSV/YAML exchange. Invalid input yields 422 with the report. */
@Tag(name = "Configuration & exchange")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class ConfigController {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv");
    private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

    private final ConfigImportService imports;
    private final CandidateImportService candidateImports;
    private final EmployerExchangeService exchange;
    private final ConfigProperties properties;

    enum Format {
        CSV,
        YAML
    }

    @Operation(summary = "Validate the configured configuration directory")
    @GetMapping("/config/validation")
    ResponseEntity<ConfigImportReport> validate() {
        return respond(imports.validate(properties.directory()));
    }

    @Operation(summary = "Import the configured configuration directory", description = "All-or-nothing; dryRun reports without writing.")
    @PostMapping("/config/import")
    ResponseEntity<ConfigImportReport> importDirectory(@RequestParam(defaultValue = "false") boolean dryRun) {
        return respond(imports.importDirectory(properties.directory(), dryRun));
    }

    @Operation(summary = "Import one YAML configuration document", description = "Any kind, e.g. an EmployerSet. Max 5 MB.")
    @PostMapping(path = "/config/documents", consumes = {"application/yaml", "application/x-yaml", "text/yaml", "text/plain"})
    ResponseEntity<ConfigImportReport> importDocument(
            @RequestParam(defaultValue = "upload.yaml") String fileName,
            @RequestParam(defaultValue = "false") boolean dryRun,
            HttpServletRequest request) throws IOException {
        return respond(imports.importDocument(fileName, RequestBodies.readText(request), dryRun));
    }

    @Operation(summary = "Import employers from CSV", description = "Header: " + "key,displayName,website,careersUrl,industry,tags,headquarters,discoveryPriority,enabled,sources. Max 5 MB.")
    @PostMapping(path = "/exchange/employers", consumes = {"text/csv", "text/plain"})
    ResponseEntity<ConfigImportReport> importEmployers(
            @RequestParam(defaultValue = "employers.csv") String fileName,
            @RequestParam(defaultValue = "false") boolean dryRun,
            HttpServletRequest request) throws IOException {
        return respond(exchange.importCsv(fileName, RequestBodies.readText(request), dryRun));
    }

    @Operation(summary = "Export employers as CSV or as a re-importable YAML EmployerSet")
    @GetMapping("/exchange/employers")
    ResponseEntity<String> exportEmployers(
            @RequestParam(defaultValue = "CSV") Format format, @RequestParam(defaultValue = "false") boolean includeArchived) {
        boolean csv = format == Format.CSV;
        return ResponseEntity.ok()
                .contentType(csv ? CSV : YAML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=employers." + (csv ? "csv" : "yaml"))
                .body(csv ? exchange.exportCsv(includeArchived) : exchange.exportYaml(includeArchived));
    }

    @Operation(summary = "Import one of your candidate profiles (YAML, kind CandidateProfile)",
            description = "Facts are marked UNVERIFIED unless trust=true; confirm them via /verification. Max 5 MB.")
    @PostMapping(path = "/me/candidate-profiles/import", consumes = {"application/yaml", "application/x-yaml", "text/yaml", "text/plain"})
    ResponseEntity<ConfigImportReport> importCandidate(
            @RequestParam(defaultValue = "candidate.yaml") String fileName,
            @RequestParam(defaultValue = "false") boolean trust,
            @RequestParam(defaultValue = "false") boolean dryRun,
            HttpServletRequest request) throws IOException {
        return respond(candidateImports.importProfile(fileName, RequestBodies.readText(request), trust, dryRun));
    }

    @Operation(summary = "Import one of your own role profiles (YAML, kind RoleProfile)", description = "Private to you. Max 5 MB.")
    @PostMapping(path = "/me/role-profiles/import", consumes = {"application/yaml", "application/x-yaml", "text/yaml", "text/plain"})
    ResponseEntity<ConfigImportReport> importRoleProfile(
            @RequestParam(defaultValue = "role-profile.yaml") String fileName,
            @RequestParam(defaultValue = "false") boolean dryRun,
            HttpServletRequest request) throws IOException {
        return respond(imports.importUserRoleProfile(fileName, RequestBodies.readText(request), dryRun));
    }

    private static ResponseEntity<ConfigImportReport> respond(ConfigImportReport report) {
        return ResponseEntity.status(report.isValid() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT).body(report);
    }
}
