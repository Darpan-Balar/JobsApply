package com.jobsapply.platform.shared.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The single JSON codec for persisted JSON (jsonb columns) and content hashing. Output is canonical — properties and
 * map keys sorted, nulls omitted — so equal content always yields the same text and hash.
 */
@Component
public class JsonCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            // Records: otherwise constructor parameters keep declaration order, so a harmless reordering of record
            // components would change content hashes.
            .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private final JsonMapper decimalReader = mapper.rebuild()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    /** Converts between compatible shapes, e.g. a settings map to a typed record. Unknown properties fail. */
    public <T> T convert(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    /**
     * Re-serializes JSON text in canonical form. Needed when comparing with stored {@code jsonb}: PostgreSQL returns
     * jsonb re-formatted (whitespace, its own key order), so equal content does not compare equal as text.
     */
    public String canonicalize(String jsonText) {
        // Decimals must keep their scale ("1.0000" stays "1.0000"): reading them as double would turn equal content
        // into a different text. PostgreSQL's numeric preserves scale in jsonb too.
        return write(decimalReader.readValue(jsonText, Object.class));
    }

    /** SHA-256 of the canonical JSON form, as 64 lower-case hex characters. */
    public String contentHash(Object value) {
        return sha256Hex(write(value));
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
