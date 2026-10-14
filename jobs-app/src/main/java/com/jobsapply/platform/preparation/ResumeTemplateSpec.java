package com.jobsapply.platform.preparation;

import com.jobsapply.spi.common.Keys;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Builder;

/**
 * A resume template: which generator selects the content, which renderers produce files, and selection limits.
 * Also the YAML schema of {@code kind: ResumeTemplate}.
 */
@Builder
public record ResumeTemplateSpec(
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
        @NotBlank String displayName,
        @Pattern(regexp = Keys.PLUGIN_ID_REGEX) String generator,
        @NotEmpty List<@Pattern(regexp = Keys.PLUGIN_ID_REGEX) String> renderers,
        @Min(1) @Max(20) Integer maxExperiences,
        @Min(0) @Max(10) Integer maxAchievementsPerExperience,
        @Min(1) @Max(60) Integer maxSkills,
        Boolean includeEducation) {

    public ResumeTemplateSpec {
        generator = generator == null ? "fact-based-resume" : generator;
        renderers = renderers == null ? List.of("html-resume", "pdf-resume") : List.copyOf(renderers);
        maxExperiences = maxExperiences == null ? 6 : maxExperiences;
        maxAchievementsPerExperience = maxAchievementsPerExperience == null ? 3 : maxAchievementsPerExperience;
        maxSkills = maxSkills == null ? 20 : maxSkills;
        includeEducation = includeEducation == null || includeEducation;
    }
}
