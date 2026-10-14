package com.jobsapply.spi.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractRecordsTest {

    @Test
    void listsAreDefensivelyCopiedAndUnmodifiable() {
        List<String> source = new ArrayList<>(List.of("a"));
        ValidationReport report = new ValidationReport(List.of(new ValidationReport.Issue("p", "m")));
        List<String> copy = Lists.immutable(source);
        source.add("b");

        assertThat(copy).containsExactly("a");
        assertThatThrownBy(() -> copy.add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(report.isValid()).isFalse();
    }

    @Test
    void nullElementsSurviveCopySoBeanValidationCanReportThem() {
        assertThat(Lists.immutable(Arrays.asList("a", null))).hasSize(2);
        assertThat(Lists.<String>immutable((List<String>) null)).isEmpty();
    }

    @Test
    void descriptorReportsDeclaredCapabilitiesOnly() {
        PluginDescriptor descriptor = PluginDescriptor.builder()
                .id("example-source")
                .version("1.0.0")
                .kind(PluginKind.JOB_SOURCE)
                .capabilities(EnumSet.of(Capability.LIST_JOBS))
                .settingsType(PluginSettings.None.class)
                .settingsSchemaVersion(1)
                .build();

        assertThat(descriptor.supports(Capability.LIST_JOBS)).isTrue();
        assertThat(descriptor.supports(Capability.APPLY_VIA_API)).isFalse();
    }

    @Test
    void personalDataIsNotExposedThroughToString() {
        CandidateContact contact = CandidateContact.builder().fullName("Jane Example").email("jane@example.com").build();
        CandidateSnapshot snapshot = CandidateSnapshot.builder()
                .contact(contact)
                .skills(List.of(CandidateSkill.builder().id("s1").verification(VerificationStatus.VERIFIED).skill("java").build()))
                .build();

        assertThat(contact.toString()).doesNotContain("Jane", "jane@example.com");
        assertThat(snapshot.toString()).doesNotContain("Jane", "java");
    }

    @Test
    void snapshotExposesAllFactsById() {
        CandidateSnapshot snapshot = CandidateSnapshot.builder()
                .skills(List.of(CandidateSkill.builder().id("skill-java").verification(VerificationStatus.VERIFIED).skill("java").build()))
                .build();

        assertThat(snapshot.fact("skill-java")).isPresent();
        assertThat(snapshot.fact("missing")).isEmpty();
    }
}
