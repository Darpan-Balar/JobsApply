package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.VerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.YearMonth;
import lombok.Builder;

@Builder(toBuilder = true)
public record CandidateEducation(
        @NotBlank @Pattern(regexp = Keys.FACT_ID_REGEX) String id,
        @NotNull VerificationStatus verification,
        @NotBlank String institution,
        @NotNull DegreeLevel level,
        String field,
        YearMonth start,
        YearMonth end,
        /* Absent = false. */
        Boolean completed) implements CandidateFact {

    public CandidateEducation {
        completed = Boolean.TRUE.equals(completed);
    }
}
