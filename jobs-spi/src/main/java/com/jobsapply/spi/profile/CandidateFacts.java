package com.jobsapply.spi.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Derived views of a candidate snapshot that only use facts which may back claims (verified, user preference or
 * derived). Unverified and superseded facts are invisible here, so they can never turn into a pass.
 */
public final class CandidateFacts {

    private CandidateFacts() {}

    public static Stream<CandidateExperience> experiences(CandidateSnapshot c) {
        return c.experiences().stream().filter(f -> f.verification().canBackClaims());
    }

    public static Stream<CandidateEducation> education(CandidateSnapshot c) {
        return c.education().stream().filter(f -> f.verification().canBackClaims());
    }

    public static Set<String> skills(CandidateSnapshot c) {
        Set<String> keys = c.skills().stream().filter(f -> f.verification().canBackClaims()).map(CandidateSkill::skill)
                .collect(Collectors.toSet());
        experiences(c).forEach(e -> keys.addAll(e.skills()));
        c.achievements().stream().filter(f -> f.verification().canBackClaims()).forEach(a -> keys.addAll(a.skills()));
        return Set.copyOf(keys);
    }

    public static Optional<CandidateAttribute> attribute(CandidateSnapshot c, String key) {
        return c.attributes().stream()
                .filter(a -> a.verification().canBackClaims() && a.key().equalsIgnoreCase(key))
                .findFirst();
    }

    /**
     * Total professional experience in years (one decimal), merging overlapping positions; an open-ended position runs
     * until {@code asOf}. Empty when there are no usable experience facts.
     */
    public static Optional<BigDecimal> totalExperienceYears(CandidateSnapshot c, YearMonth asOf) {
        List<YearMonth[]> intervals = new ArrayList<>();
        experiences(c).forEach(e -> {
            YearMonth end = e.end() == null || e.end().isAfter(asOf) ? asOf : e.end();
            if (!end.isBefore(e.start())) {
                intervals.add(new YearMonth[] {e.start(), end});
            }
        });
        if (intervals.isEmpty()) {
            return Optional.empty();
        }
        intervals.sort(Comparator.comparing(i -> i[0]));
        long months = 0;
        YearMonth start = intervals.getFirst()[0];
        YearMonth end = intervals.getFirst()[1];
        for (YearMonth[] interval : intervals.subList(1, intervals.size())) {
            if (!interval[0].isAfter(end.plusMonths(1))) {
                end = interval[1].isAfter(end) ? interval[1] : end;
            } else {
                months += monthsInclusive(start, end);
                start = interval[0];
                end = interval[1];
            }
        }
        months += monthsInclusive(start, end);
        return Optional.of(BigDecimal.valueOf(months).divide(BigDecimal.valueOf(12), 1, RoundingMode.HALF_UP));
    }

    private static long monthsInclusive(YearMonth start, YearMonth end) {
        return (end.getYear() - start.getYear()) * 12L + end.getMonthValue() - start.getMonthValue() + 1;
    }

    /** Lower-cased, trimmed. */
    public static String normalize(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
