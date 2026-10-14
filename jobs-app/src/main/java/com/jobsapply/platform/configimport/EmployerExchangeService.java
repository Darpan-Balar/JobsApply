package com.jobsapply.platform.configimport;

import com.jobsapply.platform.configimport.internal.EmployerCsv;
import com.jobsapply.platform.employer.BindingView;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.EmployerView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Employer CSV import and CSV/YAML export. Imports go through the same structural and semantic validation as YAML
 * configuration; CSV errors are reported per {@code row N.column}.
 */
@Service
@RequiredArgsConstructor
public class EmployerExchangeService {

    private final ConfigImportService importService;
    private final EmployerService employers;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    public ConfigImportReport importCsv(String fileName, String csv, boolean dryRun) {
        EmployerCsv.Parsed parsed = EmployerCsv.parse(fileName, csv);
        if (!parsed.issues().isEmpty()) {
            return new ConfigImportReport(dryRun, false, parsed.issues(), List.of(), List.of());
        }
        ConfigImportReport report = importService.importContent(fileName, ConfigKind.EMPLOYER_SET, parsed.content(), dryRun);
        return new ConfigImportReport(report.dryRun(), report.applied(),
                report.issues().stream().map(EmployerCsv::toRowPath).toList(), report.outcomes(), report.notices());
    }

    public String exportCsv(boolean includeArchived) {
        return EmployerCsv.write(employers.exportAll(includeArchived));
    }

    /** An {@code EmployerSet} document that re-imports without changes. */
    public String exportYaml(boolean includeArchived) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", ConfigKind.EMPLOYER_SET.latestSchemaVersion());
        document.put("kind", ConfigKind.EMPLOYER_SET.yamlName());
        document.put("employers", employers.exportAll(includeArchived).stream().map(EmployerExchangeService::toYaml).toList());
        return yaml.writeValueAsString(document);
    }

    private static Map<String, Object> toYaml(EmployerView e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", e.key());
        out.put("displayName", e.displayName());
        putIfPresent(out, "website", e.website());
        putIfPresent(out, "careersUrl", e.careersUrl());
        putIfPresent(out, "industry", e.industry());
        if (!e.tags().isEmpty()) {
            out.put("tags", e.tags());
        }
        putIfPresent(out, "headquarters", e.headquarters());
        out.put("discoveryPriority", e.discoveryPriority());
        out.put("enabled", e.enabled());
        if (e.metadata() != null && !e.metadata().isEmpty()) {
            out.put("metadata", e.metadata());
        }
        out.put("sources", e.bindings().stream().map(EmployerExchangeService::toYaml).toList());
        return out;
    }

    private static Map<String, Object> toYaml(BindingView b) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", b.sourceKey());
        out.put("externalRef", b.externalRef());
        out.put("enabled", b.enabled());
        out.put("priority", b.priority());
        return out;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
