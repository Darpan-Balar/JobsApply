package com.jobsapply.platform.configimport.internal;

import com.jobsapply.platform.configimport.ConfigImportReport;
import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.configimport.ConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@code jobs.config.validate-only=true}: validate, print the report, exit (0 = valid, 1 = invalid).
 * {@code jobs.config.import-on-startup=true}: import system configuration; invalid configuration fails startup.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
class ConfigStartupRunner implements ApplicationRunner {

    private final ConfigProperties properties;
    private final ConfigImportService importService;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.validateOnly()) {
            ConfigImportReport report = importService.validate(properties.directory());
            System.out.print(report.render());
            int exitCode = report.isValid() ? 0 : 1;
            System.exit(SpringApplication.exit(context, () -> exitCode));
        } else if (properties.importOnStartup()) {
            ConfigImportReport report = importService.importDirectory(properties.directory(), false);
            if (!report.isValid()) {
                throw new IllegalStateException("Invalid configuration in " + properties.directory() + ":\n" + report.render());
            }
            log.info("Configuration imported from {}:\n{}", properties.directory(), report.render());
        }
    }
}
