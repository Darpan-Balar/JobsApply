package com.jobsapply.spi.common;

/** Root of every plugin contract. A plugin is identified and described by its {@link PluginDescriptor}. */
public interface Plugin {

    PluginDescriptor descriptor();
}
