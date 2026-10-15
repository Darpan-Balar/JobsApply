package com.jobsapply.platform.resumeimport;

import com.jobsapply.spi.profile.CandidateSnapshot;
import java.util.List;

/**
 * An unsaved profile proposed from a resume. Every fact is UNVERIFIED; fields the resume did not state are empty and
 * must be completed by the candidate before saving.
 *
 * @param extractor the extractor that produced it
 * @param sentExternally whether the resume text left this machine (e.g. to Claude)
 * @param notes what to check or complete
 * @param unknownSkills skill names from the resume that are not in the skill catalog (not included)
 */
public record ProfileDraft(CandidateSnapshot snapshot, String extractor, boolean sentExternally, List<String> notes,
        List<String> unknownSkills) {

    public ProfileDraft {
        notes = List.copyOf(notes);
        unknownSkills = List.copyOf(unknownSkills);
    }

    @Override
    public String toString() {
        return "ProfileDraft[redacted]";
    }
}
