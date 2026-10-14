package com.jobsapply.platform.plugin.internal;

import com.jobsapply.platform.plugin.PluginProperties;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.source.JobSourceAdapter;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One registry per plugin kind in use. Further kinds are added in the phase that first uses them. */
@Configuration(proxyBeanMethods = false)
class PluginRegistryConfiguration {

    @Bean
    PluginRegistry<JobSourceAdapter> jobSourceAdapters(
            ObjectProvider<JobSourceAdapter> adapters, PluginProperties properties, Validator validator) {
        return new PluginRegistry<>(PluginKind.JOB_SOURCE, adapters.orderedStream().toList(), properties.disabled(), validator);
    }
}
