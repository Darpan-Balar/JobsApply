package com.jobsapply.spi.common;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;

/**
 * Self-description of a plugin, validated by the platform's plugin registry at startup.
 *
 * @param id stable unique identifier referenced from configuration (e.g. {@code greenhouse-job-board})
 * @param version implementation version of the plugin
 * @param kind plugin kind
 * @param capabilities optional operations this plugin supports
 * @param settingsType typed settings record bound from configuration
 * @param settingsSchemaVersion version of the settings schema, for configuration upgrades
 * @param compliance documentation / authentication / terms notes
 */
@Builder(toBuilder = true)
public record PluginDescriptor(
        @NotBlank @Pattern(regexp = Keys.PLUGIN_ID_REGEX) String id,
        @NotBlank String version,
        @NotNull PluginKind kind,
        Set<Capability> capabilities,
        @NotNull Class<? extends PluginSettings> settingsType,
        @Min(1) int settingsSchemaVersion,
        ComplianceNote compliance) {

    public PluginDescriptor {
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }
}
