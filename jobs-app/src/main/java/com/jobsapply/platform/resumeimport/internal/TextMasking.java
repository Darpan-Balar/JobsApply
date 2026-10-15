package com.jobsapply.platform.resumeimport.internal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes direct identifiers from resume text before it is sent to an external service: e-mail addresses, phone
 * numbers, links and the candidate's name become placeholders. Dates (e.g. "2019 – 2021") are kept.
 */
public final class TextMasking {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://\\S+|www\\.\\S+|(?:linkedin|github|gitlab)\\.com/\\S+)");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w/])\\+?\\(?\\d[\\d\\s().-]{6,}\\d(?![\\w/])");
    private static final Pattern YEARS = Pattern.compile("(19|20)\\d{2}");

    private TextMasking() {}

    public static String mask(String text, String fullName) {
        String masked = EMAIL.matcher(text).replaceAll("[email]");
        masked = URL.matcher(masked).replaceAll("[link]");
        Matcher phone = PHONE.matcher(masked);
        StringBuilder out = new StringBuilder();
        while (phone.find()) {
            String candidate = phone.group();
            String digits = candidate.replaceAll("\\D", "");
            boolean looksLikeDates = YEARS.matcher(candidate).results().count() >= 1 && digits.length() <= 8;
            phone.appendReplacement(out, Matcher.quoteReplacement(!looksLikeDates && digits.length() >= 7 ? "[phone]" : candidate));
        }
        phone.appendTail(out);
        masked = out.toString();
        if (fullName != null && !fullName.isBlank()) {
            masked = masked.replaceAll("(?i)" + Pattern.quote(fullName.strip()), "[name]");
        }
        return masked;
    }
}
