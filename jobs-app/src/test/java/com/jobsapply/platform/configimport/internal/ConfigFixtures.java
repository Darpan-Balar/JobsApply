package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.configimport.ConfigDocument;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.plugin.PluginSettingsBinder;
import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.platform.source.SourceService;
import com.jobsapply.platform.support.TestJobSourceAdapter;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.source.JobSourceAdapter;
import com.jobsapply.platform.configimport.ConfigIssue;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/** In-memory YAML fixtures for loader and validator unit tests (no Spring, no database). */
final class ConfigFixtures {

    static final Validator VALIDATOR = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();

    static final String FAMILIES = """
            schemaVersion: 1
            kind: JobFamilySet
            families:
              - { key: backend, displayName: Backend }
            """;

    static final String SKILLS = """
            schemaVersion: 1
            kind: SkillSet
            skills:
              - { key: java, displayName: Java }
              - { key: sql, displayName: SQL }
              - { key: kubernetes, displayName: Kubernetes, aliases: [k8s] }
            """;

    static final String ROLE = """
            schemaVersion: 1
            kind: RoleProfile
            key: backend-engineer
            displayName: Backend Engineer
            jobFamily: backend
            titles:
              include: [backend engineer]
              exclude: [intern]
            skills:
              required: [java]
              requiredAnyOf: [[sql]]
              preferred:
                - { skill: kubernetes, weight: 2 }
            experience: { minYears: 1, maxYears: 8 }
            eligibility:
              - type: title-match
            scoring:
              strategy: weighted
              strategyVersion: 1
              weights: { title: 0.5, required-skills: 0.5 }
              thresholds: { minimum: 50, shortlist: 70 }
            screeningQuestions:
              - { match: "(?i)visa", policy: ALWAYS_ASK_USER }
            """;

    static final String SOURCE = """
            schemaVersion: 1
            kind: JobSource
            key: greenhouse-public
            adapter: test-job-board
            displayName: Greenhouse
            settings:
              requestTimeout: PT10S
            """;

    static final String EMPLOYERS = """
            schemaVersion: 1
            kind: EmployerSet
            employers:
              - key: example-co
                displayName: Example Co
                website: https://co.example
                sources:
                  - { source: greenhouse-public, externalRef: exampleco }
            """;

    private ConfigFixtures() {}

    /** A real {@link SourceService} whose registry contains only the test adapter (no database needed for checks). */
    static SourceService sourceService() {
        PluginRegistry<JobSourceAdapter> registry =
                new PluginRegistry<>(PluginKind.JOB_SOURCE, List.of(new TestJobSourceAdapter()), Set.of(), VALIDATOR);
        JsonCodec json = new JsonCodec();
        return new SourceService(null, registry, new PluginSettingsBinder(json, VALIDATOR), json);
    }

    static ConfigLoader loader() {
        return new ConfigLoader(VALIDATOR, List.of());
    }

    /** Loads {@code file name → YAML} pairs in iteration order. */
    static LoadedConfig load(Map<String, String> files) {
        ConfigLoader loader = loader();
        List<ConfigDocument> documents = new ArrayList<>();
        List<ConfigIssue> issues = new ArrayList<>();
        files.forEach((name, yaml) ->
                loader.load(name, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), documents, issues));
        return new LoadedConfig(documents, issues);
    }

    static List<ConfigIssue> issuesOf(String yaml) {
        return load(Map.of("test.yaml", yaml)).issues();
    }
}
