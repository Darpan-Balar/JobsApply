package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.VerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * @param skill canonical skill key from the skill catalog
 * @param years years of hands-on use, or {@code null} if not stated (never guessed)
 */
@Builder(toBuilder = true)
public record CandidateSkill(
        @NotBlank @Pattern(regexp = Keys.FACT_ID_REGEX) String id,
        @NotNull VerificationStatus verification,
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String skill,
        @PositiveOrZero BigDecimal years,
        Proficiency proficiency) implements CandidateFact {

    public enum Proficiency {
        BASIC,
        WORKING,
        PROFICIENT,
        EXPERT
    }
}
