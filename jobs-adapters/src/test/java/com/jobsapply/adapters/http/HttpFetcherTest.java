package com.jobsapply.adapters.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.adapters.FakeTransport;
import com.jobsapply.adapters.Fixtures;
import com.jobsapply.adapters.RecordingSleeper;
import com.jobsapply.adapters.source.greenhouse.GreenhouseSettings;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class HttpFetcherTest {

    static final String URL = "https://api.example.test/jobs";
    final FakeTransport transport = new FakeTransport();
    final RecordingSleeper sleeper = new RecordingSleeper();
    final HttpFetcher fetcher = Fixtures.fetcher(transport, sleeper);
    final GreenhouseSettings twoRetries = new GreenhouseSettings(Duration.ofSeconds(5), 2, RateLimit.perSecond(1000));

    private PluginResult<JsonNode> get() {
        return fetcher.getJson(URI.create(URL), twoRetries);
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        transport.respond(URL, 503, "").respond(URL, 502, "").respond(URL, 200, "{\"ok\":true}");

        assertThat(get()).isInstanceOf(PluginResult.Ok.class);
        assertThat(transport.requests).hasSize(3);
        assertThat(sleeper.sleeps).filteredOn(d -> d.toMillis() >= 500).hasSize(2);
    }

    @Test
    void retriesAreBounded() {
        transport.respond(URL, 500, "");

        PluginResult<JsonNode> result = get();

        assertThat(transport.requests).hasSize(3);
        assertThat(result).isInstanceOfSatisfying(PluginResult.Failed.class, f -> {
            assertThat(f.kind()).isEqualTo(FailureKind.TRANSIENT);
            assertThat(f.message()).endsWith("(after 3 attempts)");
        });
    }

    @Test
    void notFoundAuthAndClientErrorsAreNotRetried() {
        transport.respond(URL, 404, "{}");
        assertThat(((PluginResult.Failed<JsonNode>) get()).kind()).isEqualTo(FailureKind.NOT_FOUND);
        assertThat(transport.requests).hasSize(1);

        FakeTransport t2 = new FakeTransport().respond(URL, 403, "");
        assertThat(((PluginResult.Failed<JsonNode>) Fixtures.fetcher(t2, sleeper).getJson(URI.create(URL), twoRetries)).kind())
                .isEqualTo(FailureKind.AUTH);
        FakeTransport t3 = new FakeTransport().respond(URL, 400, "");
        assertThat(((PluginResult.Failed<JsonNode>) Fixtures.fetcher(t3, sleeper).getJson(URI.create(URL), twoRetries)).kind())
                .isEqualTo(FailureKind.PERMANENT);
    }

    @Test
    void rateLimitHonoursRetryAfter() {
        transport.respond(URL, 429, "", Map.of("Retry-After", "7")).respond(URL, 200, "[]");

        assertThat(get()).isInstanceOf(PluginResult.Ok.class);
        assertThat(sleeper.sleeps).contains(Duration.ofSeconds(7));
    }

    @Test
    void retryAfterIsCapped() {
        assertThat(HttpFetcher.backoff(1, Duration.ofHours(1))).isEqualTo(HttpFetcher.MAX_RETRY_AFTER);
        assertThat(HttpFetcher.backoff(20, null)).isLessThanOrEqualTo(HttpFetcher.MAX_BACKOFF);
    }

    @Test
    void timeoutsAndNetworkErrorsAreTransient() {
        transport.fail(URL, new HttpTimeoutException("slow"));
        assertThat(get()).isInstanceOfSatisfying(PluginResult.Failed.class, f -> {
            assertThat(f.kind()).isEqualTo(FailureKind.TRANSIENT);
            assertThat(f.message()).contains("timeout");
        });
    }

    @Test
    void invalidJsonIsAParseFailureNotRetried() {
        transport.respond(URL, 200, "<html>maintenance</html>");
        assertThat(((PluginResult.Failed<JsonNode>) get()).kind()).isEqualTo(FailureKind.PARSE);
        assertThat(transport.requests).hasSize(1);
    }

    @Test
    void oversizedResponsesAreRejected() {
        transport.respond(URL, 200, "x".repeat(HttpFetcher.MAX_RESPONSE_BYTES + 1));
        assertThat(get()).isInstanceOfSatisfying(PluginResult.Failed.class, f -> assertThat(f.message()).contains("exceeds"));
    }
}
