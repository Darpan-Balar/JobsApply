/**
 * Plugin registration (ADR-0003). Plugins are Spring beans implementing an SPI interface; each kind has one
 * {@link com.jobsapply.platform.plugin.PluginRegistry} that validates descriptors and identifiers at startup and binds
 * plugin settings to the plugin's typed settings record.
 */
package com.jobsapply.platform.plugin;
