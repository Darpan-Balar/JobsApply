package com.jobsapply.platform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestPluginsConfiguration {

    @Bean
    TestJobSourceAdapter testJobSourceAdapter() {
        return new TestJobSourceAdapter();
    }
}
