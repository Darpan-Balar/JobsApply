package com.jobsapply.platform.shared.web;

import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Masks e-mail addresses and phone numbers in every string written to structured (JSON) logs — messages, exception
 * messages and stack traces included. A safety net only: code must still never log personal data on purpose.
 * Registered via {@code logging.structured.json.customizer}.
 */
public class RedactingLogCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /**
     * Clearly phone-shaped values only — international ({@code +44 20 7946 0958}) or with a parenthesized area code
     * ({@code (555) 123-4567}) — so dates, versions and numeric ids stay readable in logs.
     */
    static final Pattern PHONE = Pattern.compile(
            "(?<![\\w+])(?:\\+\\d{1,3}[ .-]?(?:\\(\\d{1,4}\\)[ .-]?)?\\d{1,4}(?:[ .-]?\\d{2,4}){2,4}|\\(\\d{3}\\)[ .-]?\\d{3}[ .-]\\d{4})(?![\\w])");

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, RedactingLogCustomizer::redact));
    }

    static String redact(String value) {
        if (value == null) {
            return null;
        }
        String masked = EMAIL.matcher(value).replaceAll("<email>");
        return PHONE.matcher(masked).replaceAll("<phone>");
    }
}
