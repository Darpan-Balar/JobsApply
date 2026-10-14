package com.jobsapply.spi.common;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves country names or codes to ISO 3166-1 alpha-2 codes using the JDK's country list (English names) plus a few
 * common aliases. Unknown values resolve to empty — never to a guess.
 */
public final class Countries {

    private static final Set<String> ISO_CODES = Set.of(Locale.getISOCountries());
    private static final Map<String, String> BY_NAME = new HashMap<>();

    static {
        for (String code : ISO_CODES) {
            BY_NAME.put(Locale.of("", code).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
        }
        Map.ofEntries(
                Map.entry("usa", "US"), Map.entry("u.s.", "US"), Map.entry("u.s.a.", "US"),
                Map.entry("united states of america", "US"),
                Map.entry("uk", "GB"), Map.entry("u.k.", "GB"), Map.entry("great britain", "GB"),
                Map.entry("england", "GB"), Map.entry("scotland", "GB"), Map.entry("wales", "GB"),
                Map.entry("northern ireland", "GB"), Map.entry("uae", "AE"), Map.entry("south korea", "KR"),
                Map.entry("korea", "KR"), Map.entry("czech republic", "CZ"), Map.entry("russia", "RU"),
                Map.entry("vietnam", "VN"), Map.entry("turkey", "TR"), Map.entry("holland", "NL"))
                .forEach(BY_NAME::put);
    }

    private Countries() {}

    public static Optional<String> toAlpha2(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.isBlank()) {
            return Optional.empty();
        }
        String value = nameOrCode.strip();
        if (value.length() == 2) {
            String upper = value.toUpperCase(Locale.ROOT);
            if (upper.equals("UK")) {
                return Optional.of("GB");
            }
            return ISO_CODES.contains(upper) ? Optional.of(upper) : Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(value.toLowerCase(Locale.ROOT)));
    }
}
