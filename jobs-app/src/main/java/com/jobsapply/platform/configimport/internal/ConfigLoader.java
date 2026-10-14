package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.configimport.ConfigDocument;
import com.jobsapply.platform.configimport.ConfigIssue;
import com.jobsapply.platform.configimport.ConfigKind;
import com.jobsapply.platform.configimport.ConfigUpgrader;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Parses configuration files: YAML → envelope check ({@code schemaVersion}, {@code kind}) → upgrader chain → typed
 * record → Bean Validation. Every problem becomes a {@link ConfigIssue}; nothing is thrown for bad input.
 */
@Component
public class ConfigLoader {

    private static final String ENVELOPE_KIND = "kind";
    private static final String ENVELOPE_VERSION = "schemaVersion";

    private final Validator validator;
    private final Map<ConfigKind, Map<Integer, ConfigUpgrader>> upgraders = new HashMap<>();
    private final YAMLMapper yaml = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public ConfigLoader(Validator validator, List<ConfigUpgrader> upgraderBeans) {
        this.validator = validator;
        for (ConfigUpgrader upgrader : upgraderBeans) {
            ConfigUpgrader previous = upgraders
                    .computeIfAbsent(upgrader.kind(), k -> new HashMap<>())
                    .put(upgrader.fromVersion(), upgrader);
            if (previous != null) {
                throw new IllegalStateException("Duplicate config upgrader for " + upgrader.kind() + " v" + upgrader.fromVersion());
            }
        }
        for (ConfigKind kind : ConfigKind.values()) {
            for (int v = 1; v < kind.latestSchemaVersion(); v++) {
                if (!upgraders.getOrDefault(kind, Map.of()).containsKey(v)) {
                    throw new IllegalStateException("Missing config upgrader for " + kind + " v" + v);
                }
            }
        }
    }

    public LoadedConfig loadDirectory(java.nio.file.Path directory) {
        if (!Files.isDirectory(directory)) {
            return new LoadedConfig(List.of(), List.of(new ConfigIssue(directory.toString(), "", "configuration directory not found")));
        }
        List<java.nio.file.Path> files;
        try (Stream<java.nio.file.Path> walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .sorted(Comparator.comparing(java.nio.file.Path::toString))
                    .toList();
        } catch (IOException e) {
            return new LoadedConfig(List.of(), List.of(new ConfigIssue(directory.toString(), "", "cannot list directory: " + e.getMessage())));
        }
        List<ConfigDocument> documents = new ArrayList<>();
        List<ConfigIssue> issues = new ArrayList<>();
        for (java.nio.file.Path file : files) {
            String relative = directory.relativize(file).toString();
            try (InputStream in = Files.newInputStream(file)) {
                load(relative, in, documents, issues);
            } catch (IOException e) {
                issues.add(new ConfigIssue(relative, "", "cannot read file: " + e.getMessage()));
            }
        }
        return new LoadedConfig(documents, issues);
    }

    /** Loads a single uploaded YAML document. */
    public LoadedConfig loadText(String file, String yaml) {
        List<ConfigDocument> documents = new ArrayList<>();
        List<ConfigIssue> issues = new ArrayList<>();
        load(file, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), documents, issues);
        return new LoadedConfig(documents, issues);
    }

    /** Structurally validates content built in code (e.g. from CSV) exactly like a parsed document. */
    public LoadedConfig fromContent(String file, ConfigKind kind, Object content) {
        List<ConfigIssue> issues = validator.validate(content).stream()
                .map(v -> new ConfigIssue(file, beanPath(v.getPropertyPath()), v.getMessage()))
                .sorted(Comparator.comparing(ConfigIssue::path).thenComparing(ConfigIssue::message))
                .toList();
        return issues.isEmpty()
                ? new LoadedConfig(List.of(new ConfigDocument(file, kind, kind.latestSchemaVersion(), content)), List.of())
                : new LoadedConfig(List.of(), issues);
    }

    /** Loads one document; visible for tests. */
    void load(String file, InputStream in, List<ConfigDocument> documents, List<ConfigIssue> issues) {
        JsonNode root;
        try {
            root = yaml.readTree(in);
        } catch (JacksonException e) {
            issues.add(new ConfigIssue(file, "", "not valid YAML: " + firstLine(e.getOriginalMessage())));
            return;
        }
        if (root == null || !root.isObject()) {
            issues.add(new ConfigIssue(file, "", "document must be a YAML mapping"));
            return;
        }
        ObjectNode body = ((ObjectNode) root).deepCopy();
        JsonNode kindNode = body.remove(ENVELOPE_KIND);
        JsonNode versionNode = body.remove(ENVELOPE_VERSION);

        String validKinds = Arrays.stream(ConfigKind.values()).map(ConfigKind::yamlName).collect(Collectors.joining(", "));
        if (kindNode == null || !kindNode.isString()) {
            issues.add(new ConfigIssue(file, ENVELOPE_KIND, "is required; one of: " + validKinds));
            return;
        }
        ConfigKind kind = ConfigKind.fromYamlName(kindNode.asString()).orElse(null);
        if (kind == null) {
            issues.add(new ConfigIssue(file, ENVELOPE_KIND, "unknown kind '" + kindNode.asString() + "'; one of: " + validKinds));
            return;
        }
        if (versionNode == null || !versionNode.isInt()) {
            issues.add(new ConfigIssue(file, ENVELOPE_VERSION, "is required and must be an integer"));
            return;
        }
        int schemaVersion = versionNode.asInt();
        if (schemaVersion < 1 || schemaVersion > kind.latestSchemaVersion()) {
            issues.add(new ConfigIssue(file, ENVELOPE_VERSION, "unsupported schemaVersion " + schemaVersion
                    + " for " + kind.yamlName() + "; supported: 1.." + kind.latestSchemaVersion()));
            return;
        }

        for (int v = schemaVersion; v < kind.latestSchemaVersion(); v++) {
            body = upgraders.get(kind).get(v).upgrade(body);
        }

        Object content;
        try {
            content = yaml.treeToValue(body, kind.contentType());
        } catch (JacksonException e) {
            issues.add(new ConfigIssue(file, jacksonPath(e), firstLine(e.getOriginalMessage())));
            return;
        }
        if (content == null) {
            issues.add(new ConfigIssue(file, "", "document has no content"));
            return;
        }
        List<ConfigIssue> violations = validator.validate(content).stream()
                .map(v -> new ConfigIssue(file, beanPath(v.getPropertyPath()), v.getMessage()))
                .sorted(Comparator.comparing(ConfigIssue::path).thenComparing(ConfigIssue::message))
                .toList();
        if (!violations.isEmpty()) {
            issues.addAll(violations);
            return;
        }
        documents.add(new ConfigDocument(file, kind, schemaVersion, content));
    }

    private static String jacksonPath(JacksonException e) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    static String beanPath(Path propertyPath) {
        StringBuilder path = new StringBuilder();
        for (Path.Node node : propertyPath) {
            if (node.isInIterable()) {
                path.append('[').append(node.getIndex() != null ? node.getIndex() : node.getKey()).append(']');
            }
            String name = node.getName();
            if (name != null && !name.startsWith("<")) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(name);
            }
        }
        return path.toString();
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "invalid value";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
