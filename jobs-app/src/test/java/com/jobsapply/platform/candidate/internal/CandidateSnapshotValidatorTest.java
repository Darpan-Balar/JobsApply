package com.jobsapply.platform.candidate.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.spi.common.VerificationStatus;
import com.jobsapply.spi.profile.CandidateAchievement;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateSnapshotValidatorTest {

    private final CandidateSnapshotValidator validator = new CandidateSnapshotValidator();

    private static CandidateExperience experience(String id, YearMonth start, YearMonth end) {
        return CandidateExperience.builder().id(id).verification(VerificationStatus.VERIFIED)
                .employer("Example Co").title("Engineer").start(start).end(end).build();
    }

    @Test
    void validSnapshotHasNoProblems() {
        CandidateSnapshot snapshot = CandidateSnapshot.builder()
                .experiences(List.of(experience("exp-1", YearMonth.of(2021, 1), null)))
                .achievements(List.of(CandidateAchievement.builder().id("ach-1").verification(VerificationStatus.VERIFIED)
                        .text("Did a thing").experienceId("exp-1").build()))
                .build();
        assertThat(validator.validate(snapshot)).isEmpty();
    }

    @Test
    void reportsDuplicateFactIdsAcrossFactTypes() {
        CandidateSnapshot snapshot = CandidateSnapshot.builder()
                .experiences(List.of(experience("same", YearMonth.of(2021, 1), null)))
                .skills(List.of(CandidateSkill.builder().id("same").verification(VerificationStatus.VERIFIED).skill("java").build()))
                .build();
        assertThat(validator.validate(snapshot)).containsExactly("duplicate fact id 'same'");
    }

    @Test
    void reportsInvertedDatesAndDanglingReferencesWithoutLeakingContent() {
        CandidateSnapshot snapshot = CandidateSnapshot.builder()
                .experiences(List.of(experience("exp-1", YearMonth.of(2022, 5), YearMonth.of(2021, 1))))
                .achievements(List.of(CandidateAchievement.builder().id("ach-1").verification(VerificationStatus.VERIFIED)
                        .text("Secret project name").experienceId("exp-404").build()))
                .build();
        List<String> problems = validator.validate(snapshot);
        assertThat(problems).containsExactlyInAnyOrder(
                "experience 'exp-1' ends before it starts",
                "achievement 'ach-1' references unknown experience 'exp-404'");
        assertThat(String.join(" ", problems)).doesNotContain("Example Co", "Secret project name");
    }
}
