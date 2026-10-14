package com.jobsapply.spi.common;

/**
 * Marker for a plugin's typed, Bean-Validation-annotated settings record. Settings are bound from configuration and
 * validated before the plugin is used. Secrets are never settings values; settings may only hold secret references.
 */
public interface PluginSettings {

    /** Settings type for plugins that need no configuration. */
    record None() implements PluginSettings {}
}
