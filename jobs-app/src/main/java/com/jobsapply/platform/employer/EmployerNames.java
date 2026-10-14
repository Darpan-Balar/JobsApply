package com.jobsapply.platform.employer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Name normalization used for duplicate detection and matching against job-board company names. */
public final class EmployerNames {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "( (inc|incorporated|llc|ltd|limited|corp|corporation|co|company|gmbh|plc|sa|ag|bv|pvt|private|pte))+$");

    private EmployerNames() {}

    /** {@code "Acme, Inc."} → {@code "acme"}; {@code "Société Générale"} → {@code "societe generale"}. */
    public static String normalize(String displayName) {
        String ascii = Normalizer.normalize(displayName, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        String simple = NON_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        String withoutSuffix = LEGAL_SUFFIX.matcher(simple).replaceAll("").strip();
        return withoutSuffix.isEmpty() ? simple : withoutSuffix;
    }
}
