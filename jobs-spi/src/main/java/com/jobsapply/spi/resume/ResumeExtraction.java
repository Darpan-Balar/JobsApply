package com.jobsapply.spi.resume;

import com.jobsapply.spi.common.Lists;
import java.time.YearMonth;
import java.util.List;

/**
 * What a resume states, before any interpretation. Every field is optional: {@code null} (or empty) means the resume
 * does not say it or the extractor could not read it — never a guess.
 *
 * @param skills skill names as written (the platform maps them onto the skill catalog)
 * @param notes what could not be read, for the candidate (e.g. "a date range for 'Acme' had no month")
 */
public record ResumeExtraction(Contact contact, List<Experience> experiences, List<Education> education, List<String> skills,
        List<String> notes) {

    public ResumeExtraction {
        experiences = Lists.immutable(experiences);
        education = Lists.immutable(education);
        skills = Lists.immutable(skills);
        notes = Lists.immutable(notes);
    }

    public record Contact(String fullName, String email, String phone, String city, String countryCode, List<String> links) {
        public Contact {
            links = Lists.immutable(links);
        }

        @Override
        public String toString() {
            return "Contact[redacted]";
        }
    }

    /**
     * @param end {@code null} when current or unknown — {@code current} tells which
     * @param highlights achievement lines in the candidate's words
     */
    public record Experience(String title, String employer, String location, YearMonth start, YearMonth end, boolean current,
            List<String> highlights) {
        public Experience {
            highlights = Lists.immutable(highlights);
        }
    }

    /**
     * @param degree the degree as written (e.g. "B.Sc.", "Master of Science")
     * @param completed {@code null} if the resume does not say
     */
    public record Education(String institution, String degree, String field, YearMonth start, YearMonth end, Boolean completed) {}

    @Override
    public String toString() {
        return "ResumeExtraction[redacted]";
    }
}
