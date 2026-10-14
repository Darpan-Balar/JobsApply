package com.jobsapply.platform.configimport;

import com.jobsapply.platform.configimport.model.EmployerSetConfig;
import com.jobsapply.platform.configimport.model.JobFamilySetConfig;
import com.jobsapply.platform.configimport.model.JobSourceConfig;
import com.jobsapply.platform.configimport.model.SkillSetConfig;
import com.jobsapply.spi.profile.RoleProfileDefinition;
import java.util.Arrays;
import java.util.Optional;

/** Document kinds, by their YAML {@code kind} value, with the latest schema version each supports. */
public enum ConfigKind {
    JOB_FAMILY_SET("JobFamilySet", JobFamilySetConfig.class, 1),
    SKILL_SET("SkillSet", SkillSetConfig.class, 1),
    ROLE_PROFILE("RoleProfile", RoleProfileDefinition.class, 1),
    JOB_SOURCE("JobSource", JobSourceConfig.class, 1),
    EMPLOYER_SET("EmployerSet", EmployerSetConfig.class, 1);

    private final String yamlName;
    private final Class<?> contentType;
    private final int latestSchemaVersion;

    ConfigKind(String yamlName, Class<?> contentType, int latestSchemaVersion) {
        this.yamlName = yamlName;
        this.contentType = contentType;
        this.latestSchemaVersion = latestSchemaVersion;
    }

    public String yamlName() {
        return yamlName;
    }

    public Class<?> contentType() {
        return contentType;
    }

    public int latestSchemaVersion() {
        return latestSchemaVersion;
    }

    public static Optional<ConfigKind> fromYamlName(String name) {
        return Arrays.stream(values()).filter(k -> k.yamlName.equals(name)).findFirst();
    }
}
