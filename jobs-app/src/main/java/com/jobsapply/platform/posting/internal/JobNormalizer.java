package com.jobsapply.platform.posting.internal;

import com.jobsapply.platform.shared.json.JsonCodec;
import com.jobsapply.spi.common.Countries;
import com.jobsapply.spi.common.EmploymentType;
import com.jobsapply.spi.common.Seniority;
import com.jobsapply.spi.common.WorkplaceType;
import com.jobsapply.spi.source.Compensation;
import com.jobsapply.spi.source.RawLocation;
import com.jobsapply.spi.source.SourceJob;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;

/**
 * Deterministic normalization of a {@link SourceJob}. Values that cannot be determined from the source stay
 * {@code UNKNOWN}/{@code null}: nothing is guessed (e.g. a title without a seniority keyword is not assumed MID, and a
 * location without "remote" is not assumed on-site).
 */
final class JobNormalizer {

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9+#]+");

    // Order matters: the first matching rule wins.
    private static final List<Map.Entry<Pattern, Seniority>> SENIORITY = List.of(
            Map.entry(word("intern|internship"), Seniority.INTERN),
            Map.entry(word("principal|distinguished"), Seniority.PRINCIPAL),
            Map.entry(word("staff"), Seniority.STAFF),
            Map.entry(word("manager|director|head of|vp|vice president"), Seniority.MANAGER),
            Map.entry(word("senior|sr|lead"), Seniority.SENIOR),
            Map.entry(word("junior|jr|entry level|graduate|new grad"), Seniority.ENTRY),
            Map.entry(word("ii|mid level|intermediate"), Seniority.MID));

    private static final List<Map.Entry<Pattern, EmploymentType>> EMPLOYMENT = List.of(
            Map.entry(Pattern.compile("intern"), EmploymentType.INTERNSHIP),
            Map.entry(Pattern.compile("part[\\s_-]?time"), EmploymentType.PART_TIME),
            Map.entry(Pattern.compile("contract|freelance"), EmploymentType.CONTRACT),
            Map.entry(Pattern.compile("\\btemp(orary)?\\b"), EmploymentType.TEMPORARY),
            Map.entry(Pattern.compile("full[\\s_-]?time|permanent"), EmploymentType.FULL_TIME));

    private JobNormalizer() {}

    static NormalizedJob normalize(UUID employerId, SourceJob job) {
        String titleNormalized = normalizeTitle(job.title());
        List<NormalizedJob.Location> locations = locations(job.locations());
        Compensation pay = job.compensation();
        boolean validPay = pay != null && pay.currency() != null && pay.currency().matches("^[A-Z]{3}$")
                && (pay.min() != null || pay.max() != null)
                && (pay.min() == null || pay.max() == null || pay.min().compareTo(pay.max()) <= 0)
                && (pay.min() == null || pay.min().signum() >= 0);
        return new NormalizedJob(
                job.title().strip(),
                titleNormalized,
                seniority(titleNormalized),
                employmentType(job.employmentTypeRaw()),
                workplaceType(job.workplaceType(), locations),
                validPay ? scaled(pay.min()) : null,
                validPay ? scaled(pay.max()) : null,
                validPay ? pay.currency() : null,
                validPay ? pay.period() : null,
                text(job.descriptionHtml()),
                locations,
                fingerprint(employerId, titleNormalized, locations));
    }

    static String normalizeTitle(String title) {
        return NON_WORD.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    static Seniority seniority(String titleNormalized) {
        for (Map.Entry<Pattern, Seniority> rule : SENIORITY) {
            if (rule.getKey().matcher(titleNormalized).find()) {
                return rule.getValue();
            }
        }
        return Seniority.UNKNOWN;
    }

    static EmploymentType employmentType(String raw) {
        if (raw == null) {
            return EmploymentType.UNKNOWN;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        for (Map.Entry<Pattern, EmploymentType> rule : EMPLOYMENT) {
            if (rule.getKey().matcher(value).find()) {
                return rule.getValue();
            }
        }
        return EmploymentType.UNKNOWN;
    }

    /** Explicit source value first; otherwise only unambiguous evidence from the locations. */
    static WorkplaceType workplaceType(WorkplaceType explicit, List<NormalizedJob.Location> locations) {
        if (explicit != null && explicit != WorkplaceType.UNKNOWN) {
            return explicit;
        }
        if (!locations.isEmpty() && locations.stream().allMatch(NormalizedJob.Location::remote)) {
            return WorkplaceType.REMOTE;
        }
        if (locations.stream().anyMatch(l -> l.rawText().toLowerCase(Locale.ROOT).contains("hybrid"))) {
            return WorkplaceType.HYBRID;
        }
        return WorkplaceType.UNKNOWN;
    }

    static List<NormalizedJob.Location> locations(List<RawLocation> raw) {
        Map<String, NormalizedJob.Location> byText = new LinkedHashMap<>();
        for (RawLocation location : raw) {
            if (location.text() == null || location.text().isBlank()) {
                continue;
            }
            String text = location.text().strip();
            boolean remote = Boolean.TRUE.equals(location.remote()) || text.toLowerCase(Locale.ROOT).matches(".*\\bremote\\b.*");
            String country = Countries.toAlpha2(location.countryCode()).orElseGet(() -> countryFromText(text));
            byText.putIfAbsent(text.toLowerCase(Locale.ROOT),
                    new NormalizedJob.Location(truncate(text, 500), location.city(), location.region(), country, remote));
        }
        return new ArrayList<>(byText.values());
    }

    /** The last comma/dash-separated segment, if it names a country ("Berlin, Germany", "Remote - United States"). */
    static String countryFromText(String text) {
        String[] parts = text.split("\\s*[,\\-–|]\\s*");
        return Countries.toAlpha2(parts[parts.length - 1]).orElse(null);
    }

    static String text(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = Jsoup.parse(html).text().strip();
        return text.isEmpty() ? null : text;
    }

    /** Same employer + same normalized title + same set of locations ⇒ likely the same vacancy on another source. */
    static String fingerprint(UUID employerId, String titleNormalized, List<NormalizedJob.Location> locations) {
        String locationKey = locations.stream()
                .map(l -> l.countryCode() != null && l.city() != null
                        ? l.countryCode() + "/" + l.city().toLowerCase(Locale.ROOT)
                        : normalizeTitle(l.rawText()))
                .sorted()
                .collect(Collectors.joining(";"));
        return JsonCodec.sha256Hex(employerId + "|" + titleNormalized + "|" + locationKey);
    }

    private static Pattern word(String alternatives) {
        return Pattern.compile("\\b(" + alternatives + ")\\b");
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
