package com.jobsapply.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Verifies module boundaries: no cycles, and no access to another module's internals (ADR-0001). */
class ModularityTest {

    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(JobsPlatformApplication.class).verify();
    }
}
