package com.jobsapply.platform.plugin;

import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.common.ValidationReport;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Binds a free-form settings map (from YAML, CSV or the API) to a plugin's typed settings record and validates it.
 * Unknown keys are rejected so that typos do not silently fall back to defaults.
 */
@Component
@RequiredArgsConstructor
public class PluginSettingsBinder {

    private final JsonCodec json;
    private final Validator validator;

    public record Binding(PluginSettings settings, ValidationReport report) {}

    public Binding bind(PluginDescriptor descriptor, Map<String, Object> settings) {
        PluginSettings bound;
        try {
            bound = json.convert(settings == null ? Map.of() : settings, descriptor.settingsType());
        } catch (JacksonException e) {
            String message = e.getOriginalMessage() == null ? "invalid settings" : e.getOriginalMessage().lines().findFirst().orElse("");
            return new Binding(null, new ValidationReport(List.of(new ValidationReport.Issue(pathOf(e), message))));
        }
        List<ValidationReport.Issue> issues = new ArrayList<>();
        validator.validate(bound).forEach(v -> issues.add(new ValidationReport.Issue(pathOf(v.getPropertyPath()), v.getMessage())));
        issues.sort(Comparator.comparing(ValidationReport.Issue::path));
        return new Binding(issues.isEmpty() ? bound : null, new ValidationReport(issues));
    }

    private static String pathOf(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String pathOf(Path propertyPath) {
        StringBuilder path = new StringBuilder();
        for (Path.Node node : propertyPath) {
            if (node.isInIterable()) {
                path.append('[').append(node.getIndex() != null ? node.getIndex() : node.getKey()).append(']');
            }
            if (node.getName() != null && !node.getName().startsWith("<")) {
                path.append(path.isEmpty() ? "" : ".").append(node.getName());
            }
        }
        return path.toString();
    }
}
