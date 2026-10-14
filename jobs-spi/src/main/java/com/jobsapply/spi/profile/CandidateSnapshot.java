package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.Lists;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Builder;

/**
 * Full content of one immutable candidate profile version. Fact ids must be unique within a snapshot (enforced by the
 * platform's validator).
 */
@Builder(toBuilder = true)
public record CandidateSnapshot(
        @Valid CandidateContact contact,
        List<@NotNull @Valid CandidateExperience> experiences,
        List<@NotNull @Valid CandidateEducation> education,
        List<@NotNull @Valid CandidateSkill> skills,
        List<@NotNull @Valid CandidateAchievement> achievements,
        List<@NotNull @Valid CandidateAttribute> attributes) {

    public CandidateSnapshot {
        experiences = Lists.immutable(experiences);
        education = Lists.immutable(education);
        skills = Lists.immutable(skills);
        achievements = Lists.immutable(achievements);
        attributes = Lists.immutable(attributes);
    }

    public Stream<CandidateFact> facts() {
        return Stream.of(experiences, education, skills, achievements, attributes)
                .flatMap(List::stream)
                .map(CandidateFact.class::cast);
    }

    public Optional<CandidateFact> fact(String factId) {
        return facts().filter(f -> f.id().equals(factId)).findFirst();
    }

    @Override
    public String toString() {
        return "CandidateSnapshot[redacted]";
    }
}
