package com.jobsapply.platform.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Asynchronous event listeners (Modulith {@code @ApplicationModuleListener}, ADR-0014) and scheduled jobs (sync,
 * retention). Each scheduled job has its own {@code enabled} property and guards itself against concurrent runs.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
class AsyncSchedulingConfiguration {}
