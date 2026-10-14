package com.jobsapply.adapters.http;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Null-safe readers for provider JSON. Missing, null and blank values all read as {@code null} — never a default. */
public final class Json {

    private Json() {}

    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isContainer()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    public static JsonNode object(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isObject() ? value : null;
    }

    public static Iterable<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isArray() ? value.values() : List.of();
    }

    public static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        try {
            return new BigDecimal(value.asString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** ISO-8601 instant with offset (e.g. {@code 2026-10-09T18:28:27-04:00}); unparseable → {@code null}. */
    public static Instant instant(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
