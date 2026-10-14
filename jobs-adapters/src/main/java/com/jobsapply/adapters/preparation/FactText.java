package com.jobsapply.adapters.preparation;

import com.jobsapply.spi.common.DegreeLevel;
import com.jobsapply.spi.profile.CandidateEducation;
import com.jobsapply.spi.profile.CandidateExperience;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fixed phrasings of facts. Only fact values plus a small, known set of glue words appear in the output. */
public final class FactText {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private FactText() {}

    public static String period(YearMonth start, YearMonth end) {
        return (start == null ? "" : MONTH.format(start)) + " – " + (end == null ? "Present" : MONTH.format(end));
    }

    public static String experience(CandidateExperience e) {
        return e.title() + ", " + e.employer() + " · " + period(e.start(), e.end());
    }

    public static String degree(DegreeLevel level) {
        return switch (level) {
            case HIGH_SCHOOL -> "High school";
            case ASSOCIATE -> "Associate degree";
            case BACHELOR -> "Bachelor's degree";
            case MASTER -> "Master's degree";
            case DOCTORATE -> "Doctorate";
        };
    }

    public static String education(CandidateEducation e) {
        StringBuilder text = new StringBuilder(degree(e.level()));
        if (e.field() != null) {
            text.append(", ").append(e.field());
        }
        text.append(" · ").append(e.institution());
        if (e.start() != null || e.end() != null) {
            text.append(" (").append(e.start() == null ? "" : e.start().getYear()).append(" – ")
                    .append(e.end() == null ? "" : e.end().getYear()).append(")");
        }
        if (!e.completed()) {
            text.append(" (in progress)");
        }
        return text.toString();
    }
}
