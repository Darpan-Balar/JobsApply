package com.jobsapply.platform.candidate.internal;

import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import com.jobsapply.spi.profile.CandidateFact;
import com.jobsapply.spi.profile.CandidateSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Cross-field rules Bean Validation cannot express. Messages reference fact ids only — never fact content (PII).
 */
@Component
public class CandidateSnapshotValidator {

    public List<String> validate(CandidateSnapshot snapshot) {
        List<String> problems = new ArrayList<>();

        Set<String> seen = new HashSet<>();
        snapshot.facts().map(CandidateFact::id).filter(id -> !seen.add(id))
                .distinct()
                .forEach(id -> problems.add("duplicate fact id '" + id + "'"));

        for (CandidateExperience experience : snapshot.experiences()) {
            if (experience.end() != null && experience.end().isBefore(experience.start())) {
                problems.add("experience '" + experience.id() + "' ends before it starts");
            }
        }
        for (CandidateEducation education : snapshot.education()) {
            if (education.start() != null && education.end() != null && education.end().isBefore(education.start())) {
                problems.add("education '" + education.id() + "' ends before it starts");
            }
        }

        Set<String> experienceIds = snapshot.experiences().stream().map(CandidateExperience::id).collect(Collectors.toSet());
        snapshot.achievements().stream()
                .filter(a -> a.experienceId() != null && !experienceIds.contains(a.experienceId()))
                .forEach(a -> problems.add("achievement '" + a.id() + "' references unknown experience '" + a.experienceId() + "'"));
        return problems;
    }
}
