package com.jobsapply.platform.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateAttribute;
import com.jobsapply.spi.profile.CandidateFact;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateSnapshotsTest {

    @Test
    void importedFactsBecomeUnverifiedButPreferencesStay() {
        CandidateSnapshot imported = CandidateSnapshot.builder()
                .skills(List.of(CandidateSkill.builder().id("s1").verification(VerificationStatus.VERIFIED).skill("java").build(),
                        CandidateSkill.builder().id("s2").verification(VerificationStatus.SUPERSEDED).skill("perl").build()))
                .attributes(List.of(CandidateAttribute.builder().id("p1").verification(VerificationStatus.USER_PREFERENCE)
                        .key("preferred-city").value("Berlin").build()))
                .build();

        CandidateSnapshot unconfirmed = CandidateSnapshots.asUnconfirmedImport(imported);

        assertThat(unconfirmed.facts().map(CandidateFact::verification)).containsExactly(
                VerificationStatus.UNVERIFIED, VerificationStatus.SUPERSEDED, VerificationStatus.USER_PREFERENCE);
    }
}
