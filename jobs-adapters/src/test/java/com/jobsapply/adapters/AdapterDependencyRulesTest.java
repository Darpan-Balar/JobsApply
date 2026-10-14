package com.jobsapply.adapters;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** ADR-0003: built-in adapters use only the SPI — they must never reach into the platform. */
class AdapterDependencyRulesTest {

    private final JavaClasses adapters = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.jobsapply.adapters");

    @Test
    void adaptersDoNotDependOnThePlatform() {
        noClasses().should().dependOnClassesThat().resideInAPackage("com.jobsapply.platform..").check(adapters);
    }

    @Test
    void onlyTheRegistrationClassKnowsSpring() {
        noClasses().that().doNotHaveSimpleName("BuiltInAdaptersAutoConfiguration")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .check(adapters);
    }
}
