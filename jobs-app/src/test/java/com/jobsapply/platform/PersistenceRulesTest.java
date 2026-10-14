package com.jobsapply.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

/**
 * ADR-0002: Hibernate's {@code @TenantId} filter does not apply to native SQL, so native repository queries would
 * silently bypass per-user isolation. They are banned in production code; introducing one requires changing this test
 * together with an explicit owner predicate and an isolation test.
 */
class PersistenceRulesTest {

    private final JavaClasses production = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.jobsapply.platform");

    private static final DescribedPredicate<JavaAnnotation<?>> NATIVE_QUERY = DescribedPredicate.describe(
            "a native SQL query",
            annotation -> annotation.getRawType().isEquivalentTo(NativeQuery.class)
                    || (annotation.getRawType().isEquivalentTo(Query.class)
                            && Boolean.TRUE.equals(annotation.get("nativeQuery").orElse(false))));

    @Test
    void repositoriesDoNotUseNativeQueries() {
        noMethods().should().beAnnotatedWith(NATIVE_QUERY).check(production);
    }
}
