package com.jobsapply.adapters.matching;

import com.jobsapply.spi.common.ComplianceNote;
import com.jobsapply.spi.common.PluginDescriptor;
import com.jobsapply.spi.common.PluginKind;
import com.jobsapply.spi.common.PluginSettings;
import com.jobsapply.spi.common.RuleVerdict;
import com.jobsapply.spi.matching.ComponentScore;
import com.jobsapply.spi.matching.JobSnapshot;
import com.jobsapply.spi.matching.RuleOutcome;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.RawLocation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared helpers for the built-in matching plugins. */
public final class MatchingSupport {

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9+#]+");
    private static final Pattern YEARS_THEN_EXPERIENCE = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*(?:\\+|plus)?\\s*(?:(?:-|–|to)\\s*\\d{1,2}\\s*\\+?\\s*)?(?:years?|yrs?)\\b[^.\\n]{0,60}?\\bexperience");
    private static final Pattern EXPERIENCE_THEN_YEARS = Pattern.compile(
            "(?i)\\bexperience\\b[^.\\n]{0,40}?\\b(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b");

    private MatchingSupport() {}

    static PluginDescriptor descriptor(String id, PluginKind kind) {
        return PluginDescriptor.builder()
                .id(id)
                .version("1.0.0")
                .kind(kind)
                .settingsType(PluginSettings.None.class)
                .settingsSchemaVersion(1)
                .compliance(new ComplianceNote(null, false, "built-in, deterministic"))
                .build();
    }

    public static String normalize(String text) {
        return text == null ? "" : NON_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** Whole-phrase containment on normalized text ("engineer" does not match "engineering"). */
    public static boolean containsPhrase(String normalizedText, String phrase) {
        String p = normalize(phrase);
        return !p.isEmpty() && (" " + normalizedText + " ").contains(" " + p + " ");
    }

    public static Optional<String> firstPhrase(String normalizedText, Collection<String> phrases) {
        return phrases.stream().filter(p -> containsPhrase(normalizedText, p)).findFirst();
    }

    /** ISO country codes of the job's locations that are known. */
    public static Set<String> jobCountries(JobSnapshot job) {
        Set<String> countries = new LinkedHashSet<>();
        for (RawLocation location : job.locations()) {
            if (location.countryCode() != null) {
                countries.add(location.countryCode());
            }
        }
        return countries;
    }

    /**
     * Years of experience a job asks for, from phrases like "5+ years of experience" or "experience: 3 years".
     * The largest stated number (capped at 30) is used; absent when the text states none.
     */
    public static Optional<Integer> requiredYears(JobSnapshot job) {
        String text = job.descriptionText();
        if (text == null) {
            return Optional.empty();
        }
        List<Integer> found = new ArrayList<>();
        for (Pattern pattern : List.of(YEARS_THEN_EXPERIENCE, EXPERIENCE_THEN_YEARS)) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                int years = Integer.parseInt(m.group(1));
                if (years > 0 && years <= 30) {
                    found.add(years);
                }
            }
        }
        return found.stream().max(Integer::compare);
    }

    /** Annualized upper end of a published pay range; empty when amount or period is unknown. */
    public static Optional<BigDecimal> annualTop(Compensation pay) {
        if (pay == null || pay.period() == null) {
            return Optional.empty();
        }
        BigDecimal top = pay.max() != null ? pay.max() : pay.min();
        if (top == null) {
            return Optional.empty();
        }
        int factor = switch (pay.period()) {
            case YEAR -> 1;
            case MONTH -> 12;
            case WEEK -> 52;
            case DAY -> 260;
            case HOUR -> 2080;
        };
        return Optional.of(top.multiply(BigDecimal.valueOf(factor)));
    }

    @SuppressWarnings("unchecked")
    static List<String> stringList(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Collection<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return value == null ? List.of() : List.of(String.valueOf(value));
    }

    static Integer integer(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value).strip());
    }

    static RuleOutcome pass(String type, String why) {
        return new RuleOutcome(type, RuleVerdict.PASS, why, List.of());
    }

    static RuleOutcome fail(String type, String why) {
        return new RuleOutcome(type, RuleVerdict.FAIL, why, List.of());
    }

    static RuleOutcome unknown(String type, String why) {
        return new RuleOutcome(type, RuleVerdict.UNKNOWN, why, List.of());
    }

    static ComponentScore noEvidence(String component, String why) {
        return new ComponentScore(component, null, List.of(), List.of(), List.of(why));
    }

    static BigDecimal ratio(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(1, value))).setScale(4, RoundingMode.HALF_UP);
    }
}
