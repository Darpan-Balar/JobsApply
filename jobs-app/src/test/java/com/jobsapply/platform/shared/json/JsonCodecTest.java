package com.jobsapply.platform.shared.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonCodecTest {

    private final JsonCodec codec = new JsonCodec();

    record Sample(String b, String a, Map<String, Integer> m, String absent) {}

    @Test
    void outputIsCanonicalSoEqualContentHasEqualHash() {
        Map<String, Integer> m1 = new LinkedHashMap<>();
        m1.put("z", 1);
        m1.put("a", 2);
        Map<String, Integer> m2 = new LinkedHashMap<>();
        m2.put("a", 2);
        m2.put("z", 1);

        String json = codec.write(new Sample("2", "1", m1, null));

        assertThat(json).isEqualTo("{\"a\":\"1\",\"b\":\"2\",\"m\":{\"a\":2,\"z\":1}}");
        assertThat(codec.contentHash(new Sample("2", "1", m1, null))).isEqualTo(codec.contentHash(new Sample("2", "1", m2, null)));
        assertThat(codec.contentHash(new Sample("2", "1", m1, null))).hasSize(64);
    }

    @Test
    void roundTrips() {
        Sample sample = new Sample("x", "y", Map.of("k", 1), null);
        assertThat(codec.read(codec.write(sample), Sample.class)).isEqualTo(sample);
    }

    @Test
    void canonicalizeKeepsDecimalScale() {
        String ours = codec.write(Map.of("score", new BigDecimal("1.0000"), "n", 3));
        assertThat(codec.canonicalize("{\"score\": 1.0000, \"n\": 3}")).isEqualTo(ours);
    }

    @Test
    void canonicalizeMakesPostgresFormattedJsonbComparable() {
        // jsonb output: whitespace after separators and PostgreSQL's own key order (shorter keys first).
        String fromJsonb = "{\"rateLimit\": {\"per\": \"PT1S\", \"permits\": 5}, \"maxRetries\": 3}";
        String ours = codec.write(Map.of("maxRetries", 3, "rateLimit", Map.of("permits", 5, "per", "PT1S")));

        assertThat(fromJsonb).isNotEqualTo(ours);
        assertThat(codec.canonicalize(fromJsonb)).isEqualTo(ours);
    }
}
