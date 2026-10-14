package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.common.VerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.YearMonth;
import java.util.List;
import lombok.Builder;

/** @param end {@code null} while the position is current */
@Builder(toBuilder = true)
public record CandidateExperience(
        @NotBlank @Pattern(regexp = Keys.FACT_ID_REGEX) String id,
        @NotNull VerificationStatus verification,
        @NotBlank String employer,
        @NotBlank String title,
        @NotNull YearMonth start,
        YearMonth end,
        String location,
        List<@NotBlank String> skills) implements CandidateFact {

    public CandidateExperience {
        skills = Lists.immutable(skills);
    }
}
