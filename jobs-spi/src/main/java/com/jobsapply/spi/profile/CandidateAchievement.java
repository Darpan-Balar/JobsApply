package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.common.VerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Builder;

/**
 * A concrete accomplishment, in the candidate's own words.
 *
 * @param experienceId optional id of the {@link CandidateExperience} it belongs to
 */
@Builder(toBuilder = true)
public record CandidateAchievement(
        @NotBlank @Pattern(regexp = Keys.FACT_ID_REGEX) String id,
        @NotNull VerificationStatus verification,
        @NotBlank String text,
        String experienceId,
        List<@NotBlank String> skills) implements CandidateFact {

    public CandidateAchievement {
        skills = Lists.immutable(skills);
    }
}
