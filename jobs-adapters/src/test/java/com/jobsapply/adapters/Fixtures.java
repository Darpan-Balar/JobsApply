package com.jobsapply.adapters;

import com.jobsapply.adapters.http.HostRateLimiter;
import com.jobsapply.adapters.http.HttpFetcher;
import com.jobsapply.spi.source.DiscoveryRequest;
import com.jobsapply.spi.source.SourceBinding;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

public final class Fixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);

    private Fixtures() {}

    public static HttpFetcher fetcher(FakeTransport transport, RecordingSleeper sleeper) {
        return new HttpFetcher(transport, new HostRateLimiter(CLOCK, sleeper), sleeper);
    }

    public static SourceBinding binding(String externalRef, Map<String, Object> settings) {
        return new SourceBinding("test-source", externalRef, settings);
    }

    public static DiscoveryRequest firstPage(SourceBinding binding) {
        return new DiscoveryRequest(binding, null, 100, null);
    }
}
