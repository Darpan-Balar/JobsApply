package com.jobsapply.spi.profile;

import com.jobsapply.spi.common.VerificationStatus;

/**
 * A single statement about the candidate. {@link #id()} is stable across profile versions so that generated claims can
 * cite it via {@link FactRef}.
 */
public sealed interface CandidateFact
        permits CandidateExperience, CandidateEducation, CandidateSkill, CandidateAchievement, CandidateAttribute {

    String id();

    VerificationStatus verification();
}
