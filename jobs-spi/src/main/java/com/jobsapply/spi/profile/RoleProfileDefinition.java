package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * A role family search profile, authored in YAML ({@code kind: RoleProfile}) and stored as an immutable version.
 * Different role families may use entirely different criteria; nothing here is specific to software roles.
 *
 * <p>Structural constraints are Bean Validation annotations. Cross-field and cross-reference rules (skill keys exist,
 * weights sum to 1, threshold ordering, rule types registered) are enforced by the platform's semantic validator.
 */
@Builder(toBuilder = true)
public record RoleProfileDefinition(
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String jobFamily,
        /* Marks illustrative sample content that is not anyone's verified qualification. Absent = false. */
        Boolean example,
        @NotNull @Valid TitleRules titles,
        List<@NotBlank String> searchTerms,
        @NotNull @Valid SkillRequirements skills,
        @Valid ExperienceRange experience,
        @Valid EducationRequirement education,
        @Valid LocationPreferences locations,
        List<@NotNull EmploymentType> employmentTypes,
        @Valid CompensationPreference compensation,
        List<@NotNull @Valid RuleSpec> eligibility,
        @NotNull @Valid ScoringConfig scoring,
        @Valid DocumentPreferences documents,
        List<@NotNull @Valid ScreeningQuestionRule> screeningQuestions) {

    public RoleProfileDefinition {
        example = Boolean.TRUE.equals(example);
        searchTerms = Lists.immutable(searchTerms);
        employmentTypes = Lists.immutable(employmentTypes);
        eligibility = Lists.immutable(eligibility);
        screeningQuestions = Lists.immutable(screeningQuestions);
    }

    /** Title inclusion/exclusion phrases, matched case-insensitively against normalized job titles. */
    @Builder
    public record TitleRules(
            @NotEmpty List<@NotBlank String> include,
            List<@NotBlank String> exclude,
            List<@NotNull Seniority> seniority) {
        public TitleRules {
            include = Lists.immutable(include);
            exclude = Lists.immutable(exclude);
            seniority = Lists.immutable(seniority);
        }
    }

    /**
     * Skill keys refer to the skill catalog (canonical keys, not aliases).
     *
     * @param required every listed skill is required
     * @param requiredAnyOf each inner group requires at least one of its skills
     * @param preferred soft, weighted skills
     */
    @Builder
    public record SkillRequirements(
            List<@NotBlank String> required,
            List<@NotEmpty List<@NotBlank String>> requiredAnyOf,
            List<@NotNull @Valid WeightedSkill> preferred) {
        public SkillRequirements {
            required = Lists.immutable(required);
            requiredAnyOf = Lists.immutable(requiredAnyOf);
            preferred = Lists.immutable(preferred);
        }
    }

    /** @param weight relative weight among preferred skills; defaults to 1 */
    public record WeightedSkill(@NotBlank String skill, @Positive Integer weight) {
        public WeightedSkill {
            weight = weight == null ? 1 : weight;
        }
    }

    public record ExperienceRange(@PositiveOrZero Integer minYears, @PositiveOrZero Integer maxYears) {}

    public record EducationRequirement(List<@NotBlank String> fields, DegreeLevel minimumLevel) {
        public EducationRequirement {
            fields = Lists.immutable(fields);
        }
    }

    public record LocationPreferences(
            List<@NotNull WorkplaceType> workplaceTypes, List<@NotNull @Valid LocationPreference> preferred) {
        public LocationPreferences {
            workplaceTypes = Lists.immutable(workplaceTypes);
            preferred = Lists.immutable(preferred);
        }
    }

    /** @param country ISO 3166-1 alpha-2 code */
    public record LocationPreference(@NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country, String region, String city) {}

    /** @param currency ISO 4217 code */
    public record CompensationPreference(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @PositiveOrZero BigDecimal minAnnual,
            CompensationPolicy whenMissing) {
        public CompensationPreference {
            whenMissing = whenMissing == null ? CompensationPolicy.IGNORE : whenMissing;
        }
    }

    /** How to treat a job that publishes no compensation. */
    public enum CompensationPolicy {
        IGNORE,
        TREAT_AS_UNKNOWN
    }

    /**
     * A hard eligibility rule reference. {@code type} names a registered eligibility-rule plugin; {@code params} are
     * that plugin's parameters, validated by the plugin.
     */
    public record RuleSpec(@NotBlank @Pattern(regexp = Keys.KEY_REGEX) String type, Map<String, Object> params) {
        public RuleSpec {
            params = Lists.immutable(params);
        }
    }

    /**
     * Soft scoring configuration.
     *
     * @param weights score-component key → weight in [0, 1]; must sum to 1
     */
    @Builder
    public record ScoringConfig(
            @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String strategy,
            @NotNull @Positive Integer strategyVersion,
            @NotEmpty Map<@NotBlank String, @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal> weights,
            @NotNull @Valid Thresholds thresholds) {
        public ScoringConfig {
            weights = Lists.immutable(weights);
        }
    }

    /** Score thresholds on a 0–100 scale; {@code minimum <= shortlist}. */
    public record Thresholds(
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minimum,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal shortlist) {}

    public record DocumentPreferences(
            @Pattern(regexp = Keys.KEY_REGEX) String resumeTemplate, @Valid CoverNotePreference coverNote) {}

    public record CoverNotePreference(@NotBlank @Pattern(regexp = Keys.KEY_REGEX) String strategy) {}

    /**
     * How to answer screening questions whose text matches {@code match} (a Java regular expression).
     *
     * @param fact for {@link ScreeningPolicy#FROM_FACT}: the candidate fact/attribute key that supplies the answer
     */
    public record ScreeningQuestionRule(@NotBlank String match, @NotNull ScreeningPolicy policy, String fact) {}

    public enum ScreeningPolicy {
        /** Never draft an answer; the candidate must answer (e.g. sponsorship, salary). */
        ALWAYS_ASK_USER,
        /** Answer only from the named verified fact; if absent, ask the user. */
        FROM_FACT,
        /** Leave optional questions blank. */
        SKIP
    }
}
