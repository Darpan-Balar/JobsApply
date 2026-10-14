package com.jobsapply.platform.configimport.model;

import com.jobsapply.spi.common.Keys;
import com.jobsapply.spi.common.Lists;
import com.jobsapply.spi.profile.CandidateAchievement;
import com.jobsapply.spi.profile.CandidateAttribute;
import com.jobsapply.spi.profile.CandidateContact;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateSkill;
import com.jobsapply.spi.profile.CandidateSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code kind: CandidateProfile} — personal data. Imported through the API into the current user's private profiles;
 * never accepted from the (git-tracked) configuration directory.
 */
public record CandidateProfileDocument(
        @NotBlank @Pattern(regexp = Keys.KEY_REGEX) String key,
        @NotBlank @Size(max = 200) String displayName,
        @Valid CandidateContact contact,
        List<@NotNull @Valid CandidateExperience> experiences,
        List<@NotNull @Valid CandidateEducation> education,
        List<@NotNull @Valid CandidateSkill> skills,
        List<@NotNull @Valid CandidateAchievement> achievements,
        List<@NotNull @Valid CandidateAttribute> attributes) {

    public CandidateProfileDocument {
        experiences = Lists.immutable(experiences);
        education = Lists.immutable(education);
        skills = Lists.immutable(skills);
        achievements = Lists.immutable(achievements);
        attributes = Lists.immutable(attributes);
    }

    public CandidateSnapshot toSnapshot() {
        return new CandidateSnapshot(contact, experiences, education, skills, achievements, attributes);
    }

    @Override
    public String toString() {
        return "CandidateProfileDocument[key=" + key + ", redacted]";
    }
}
