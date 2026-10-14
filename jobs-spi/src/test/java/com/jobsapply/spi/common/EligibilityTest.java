package com.jobsapply.spi.common;

import static com.jobsapply.spi.common.RuleVerdict.FAIL;
import static com.jobsapply.spi.common.RuleVerdict.PASS;
import static com.jobsapply.spi.common.RuleVerdict.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EligibilityTest {

    @Test
    void anyFailMakesCandidateNotEligibleEvenWithUnknowns() {
        assertThat(Eligibility.fromVerdicts(List.of(PASS, UNKNOWN, FAIL))).isEqualTo(Eligibility.NOT_ELIGIBLE);
    }

    @Test
    void unknownIsNeverTreatedAsPass() {
        assertThat(Eligibility.fromVerdicts(List.of(PASS, UNKNOWN))).isEqualTo(Eligibility.UNKNOWN);
    }

    @Test
    void allPassIsEligible() {
        assertThat(Eligibility.fromVerdicts(List.of(PASS, PASS))).isEqualTo(Eligibility.ELIGIBLE);
    }

    @Test
    void noRulesIsEligible() {
        assertThat(Eligibility.fromVerdicts(List.of())).isEqualTo(Eligibility.ELIGIBLE);
    }

    @Test
    void onlyVerifiedPreferenceOrDerivedFactsMayBackClaims() {
        assertThat(VerificationStatus.VERIFIED.canBackClaims()).isTrue();
        assertThat(VerificationStatus.USER_PREFERENCE.canBackClaims()).isTrue();
        assertThat(VerificationStatus.DERIVED.canBackClaims()).isTrue();
        assertThat(VerificationStatus.UNVERIFIED.canBackClaims()).isFalse();
        assertThat(VerificationStatus.SUPERSEDED.canBackClaims()).isFalse();
    }
}
