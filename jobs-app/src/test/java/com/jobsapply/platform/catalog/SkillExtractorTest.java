package com.jobsapply.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillExtractorTest {

    final SkillExtractor extractor = new SkillExtractor(Map.of(
            "java", Set.of("java"),
            "javascript", Set.of("javascript", "js"),
            "cpp", Set.of("cpp", "c++"),
            "csharp", Set.of("c#"),
            "spring-boot", Set.of("spring boot", "spring-boot"),
            "go", Set.of("golang"),
            "kubernetes", Set.of("kubernetes", "k8s")));

    @Test
    void matchesWholeTermsCaseInsensitively() {
        assertThat(extractor.extract("Senior JAVA engineer. Java, Spring  Boot and K8s.")).containsExactlyInAnyOrder("java", "spring-boot", "kubernetes");
    }

    @Test
    void doesNotMatchInsideOtherWords() {
        assertThat(extractor.extract("JavaScript and Javanese culture")).containsExactly("javascript");
        assertThat(extractor.extract("java.util.List is not Java")).containsExactly("java");
    }

    @Test
    void handlesSymbolsInTerms() {
        assertThat(extractor.extract("Modern C++ (C++17) and C# services")).containsExactlyInAnyOrder("cpp", "csharp");
    }

    @Test
    void aliasOnlySkillsIgnoreTheOrdinaryWord() {
        assertThat(extractor.extract("We go to market fast")).isEmpty();
        assertThat(extractor.extract("Services in Golang")).containsExactly("go");
    }

    @Test
    void emptyCatalogFindsNothing() {
        assertThat(new SkillExtractor(Map.of()).extract("Java")).isEmpty();
        assertThat(extractor.extract((String) null)).isEmpty();
    }
}
