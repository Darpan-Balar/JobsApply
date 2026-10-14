package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.configimport.model.EmployerSetConfig;
import com.jobsapply.platform.configimport.model.EmployerSetConfig.EmployerConfig;
import com.jobsapply.platform.configimport.model.EmployerSetConfig.EmployerSourceConfig;
import com.jobsapply.platform.employer.BindingView;
import com.jobsapply.platform.employer.EmployerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvReadFeature;

/**
 * CSV codec for employers. Columns: {@value #HEADER}. {@code tags} are {@code ;}-separated; {@code sources} are
 * {@code ;}-separated {@code sourceKey:externalRef[:priority]} entries listing the employer's <em>enabled</em> bindings.
 * Metadata cannot be expressed in CSV and is left unchanged by CSV imports.
 */
public final class EmployerCsv {

    public static final String HEADER = "key,displayName,website,careersUrl,industry,tags,headquarters,discoveryPriority,enabled,sources";
    private static final List<String> COLUMNS = Arrays.asList(HEADER.split(","));
    private static final Set<String> REQUIRED = Set.of("key", "displayName");
    private static final Pattern ROW_PATH = Pattern.compile("^employers\\[(\\d+)](.*)$");
    private static final CsvMapper CSV = CsvMapper.builder().enable(CsvReadFeature.WRAP_AS_ARRAY).build();

    private EmployerCsv() {}

    public record Parsed(EmployerSetConfig content, List<ConfigIssue> issues) {}

    public static Parsed parse(String file, String text) {
        List<ConfigIssue> issues = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        try (MappingIterator<String[]> iterator = CSV.readerFor(String[].class).readValues(text)) {
            while (iterator.hasNextValue()) {
                rows.add(iterator.nextValue());
            }
        } catch (JacksonException e) {
            issues.add(new ConfigIssue(file, "", "malformed CSV: " + e.getOriginalMessage().lines().findFirst().orElse("")));
            return new Parsed(null, issues);
        }
        rows.removeIf(row -> Arrays.stream(row).allMatch(cell -> cell == null || cell.isBlank()));
        if (rows.isEmpty()) {
            issues.add(new ConfigIssue(file, "", "empty file; expected header: " + HEADER));
            return new Parsed(null, issues);
        }

        List<String> header = Arrays.stream(rows.getFirst()).map(String::strip).toList();
        for (int c = 0; c < header.size(); c++) {
            if (!COLUMNS.contains(header.get(c))) {
                issues.add(new ConfigIssue(file, "header", "unknown column '" + header.get(c) + "'; expected: " + HEADER));
            }
        }
        REQUIRED.stream().filter(r -> !header.contains(r)).sorted()
                .forEach(r -> issues.add(new ConfigIssue(file, "header", "missing required column '" + r + "'")));
        if (!issues.isEmpty()) {
            return new Parsed(null, issues);
        }

        List<EmployerConfig> employers = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            Map<String, String> cells = new LinkedHashMap<>();
            String[] row = rows.get(r);
            for (int c = 0; c < header.size(); c++) {
                String value = c < row.length ? row[c] : null;
                cells.put(header.get(c), value == null || value.isBlank() ? null : value.strip());
            }
            String rowPath = "row " + (r + 1);
            employers.add(new EmployerConfig(
                    cells.get("key"), cells.get("displayName"), cells.get("website"), cells.get("careersUrl"),
                    cells.get("industry"), split(cells.get("tags")), cells.get("headquarters"),
                    integer(cells.get("discoveryPriority"), file, rowPath + ".discoveryPriority", issues),
                    bool(cells.get("enabled"), file, rowPath + ".enabled", issues),
                    null,
                    sources(cells.get("sources"), file, rowPath + ".sources", issues)));
        }
        return new Parsed(issues.isEmpty() ? new EmployerSetConfig(employers) : null, issues);
    }

    /** Rewrites {@code employers[i].field} paths to the CSV's {@code row N.field} (row 1 is the header). */
    public static ConfigIssue toRowPath(ConfigIssue issue) {
        Matcher m = ROW_PATH.matcher(issue.path());
        return m.matches()
                ? new ConfigIssue(issue.file(), "row " + (Integer.parseInt(m.group(1)) + 2) + m.group(2), issue.message())
                : issue;
    }

    public static String write(List<EmployerView> employers) {
        StringBuilder out = new StringBuilder(HEADER).append("\r\n");
        for (EmployerView e : employers) {
            List<String> sources = e.bindings().stream().filter(BindingView::enabled)
                    .map(b -> b.sourceKey() + ":" + b.externalRef() + (b.priority() == 50 ? "" : ":" + b.priority()))
                    .toList();
            List<String> cells = List.of(
                    text(e.key()), text(e.displayName()), text(e.website()), text(e.careersUrl()), text(e.industry()),
                    text(String.join(";", e.tags())), text(e.headquarters()), String.valueOf(e.discoveryPriority()),
                    String.valueOf(e.enabled()), text(String.join(";", sources)));
            out.append(String.join(",", cells.stream().map(EmployerCsv::quote).toList())).append("\r\n");
        }
        return out.toString();
    }

    /**
     * Neutralizes spreadsheet formula injection: text cells starting with {@code = + - @}, tab or CR get a leading
     * apostrophe so spreadsheet applications treat them as text.
     */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    /** RFC 4180 quoting. */
    static String quote(String value) {
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
                || (!value.isEmpty() && (Character.isWhitespace(value.charAt(0)) || Character.isWhitespace(value.charAt(value.length() - 1))));
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static List<String> split(String value) {
        return value == null ? List.of() : Arrays.stream(value.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static Integer integer(String value, String file, String path, List<ConfigIssue> issues) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            issues.add(new ConfigIssue(file, path, "not a whole number: '" + value + "'"));
            return null;
        }
    }

    private static Boolean bool(String value, String file, String path, List<ConfigIssue> issues) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> {
                issues.add(new ConfigIssue(file, path, "expected true or false, got '" + value + "'"));
                yield null;
            }
        };
    }

    private static List<EmployerSourceConfig> sources(String value, String file, String path, List<ConfigIssue> issues) {
        List<EmployerSourceConfig> result = new ArrayList<>();
        List<String> entries = split(value);
        for (int i = 0; i < entries.size(); i++) {
            String[] parts = entries.get(i).split(":", -1);
            if (parts.length < 2 || parts.length > 3 || parts[0].isBlank() || parts[1].isBlank()) {
                issues.add(new ConfigIssue(file, path + "[" + i + "]", "expected sourceKey:externalRef[:priority], got '" + entries.get(i) + "'"));
                continue;
            }
            Integer priority = parts.length == 3 ? integer(parts[2].strip(), file, path + "[" + i + "].priority", issues) : null;
            result.add(new EmployerSourceConfig(parts[0].strip(), parts[1].strip(), true, priority));
        }
        return result;
    }
}
