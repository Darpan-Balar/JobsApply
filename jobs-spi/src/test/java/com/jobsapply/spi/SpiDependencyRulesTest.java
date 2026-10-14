package com.jobsapply.spi;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** ADR-0003: the SPI is a stable, framework-free contract that third-party plugins compile against. */
class SpiDependencyRulesTest {

    private final JavaClasses spi = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.jobsapply.spi");

    @Test
    void spiDoesNotDependOnFrameworksOrPersistence() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "org.hibernate..", "tools.jackson..",
                        "com.fasterxml.jackson..")
                .check(spi);
    }

    /**
     * Lombok is compile-time only. The sole permitted trace is {@code @lombok.Generated} (CLASS retention, never loaded
     * at runtime), which lets coverage tools exclude generated code.
     */
    @Test
    void spiHasNoRuntimeDependencyOnLombok() {
        noClasses()
                .should()
                .dependOnClassesThat(resideInAPackage("lombok..").and(not(name("lombok.Generated"))))
                .check(spi);
    }

    @Test
    void spiDoesNotDependOnPlatformOrAdapters() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.jobsapply.platform..", "com.jobsapply.adapters..")
                .check(spi);
    }
}
