package com.jobsapply.adapters.http;

import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches and parses JSON with the platform's resilience rules:
 *
 * <ul>
 *   <li>rate limiting per host, before every attempt (including retries);
 *   <li>at most {@code 1 + maxRetries} attempts, retrying only transient failures (network, timeout, 5xx) and 429;
 *   <li>exponential backoff with jitter, honouring {@code Retry-After} (capped);
 *   <li>a response-size cap;
 *   <li>status mapping: 404 → NOT_FOUND, 401/403 → AUTH, 429 → RATE_LIMITED, 5xx → TRANSIENT, other 4xx → PERMANENT,
 *       unreadable body → PARSE.
 * </ul>
 */
public final class HttpFetcher {

    public static final int MAX_RESPONSE_BYTES = 25 * 1024 * 1024;
    static final Duration BASE_BACKOFF = Duration.ofMillis(500);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(10);
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);
    private static final Map<String, String> HEADERS = Map.of(
            "Accept", "application/json",
            "User-Agent", "jobs-platform/0.1 (personal job search; local)");

    private final HttpTransport transport;
    private final HostRateLimiter rateLimiter;
    private final Sleeper sleeper;
    private final JsonMapper json = JsonMapper.builder().build();

    public HttpFetcher(HttpTransport transport, HostRateLimiter rateLimiter, Sleeper sleeper) {
        this.transport = transport;
        this.rateLimiter = rateLimiter;
        this.sleeper = sleeper;
    }

    public PluginResult<JsonNode> getJson(URI uri, HttpSourceSettings settings) {
        int maxAttempts = 1 + (settings.maxRetries() == null ? HttpSourceSettings.DEFAULT_MAX_RETRIES : settings.maxRetries());
        RateLimit limit = settings.rateLimit() == null ? HttpSourceSettings.DEFAULT_RATE_LIMIT : settings.rateLimit();
        Duration timeout = HttpSourceSettings.timeoutOrDefault(settings.requestTimeout());
        PluginResult.Failed<JsonNode> last = null;
        try {
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                if (attempt > 0) {
                    sleeper.sleep(backoff(attempt, last.retryAfter()));
                }
                rateLimiter.acquire(uri.getHost(), limit);
                PluginResult<JsonNode> result = attempt(uri, timeout);
                if (!(result instanceof PluginResult.Failed<JsonNode> failed) || !retryable(failed.kind())) {
                    return result;
                }
                last = failed;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PluginResult.failed(FailureKind.TRANSIENT, "interrupted");
        }
        return new PluginResult.Failed<>(last.kind(), last.message() + " (after " + maxAttempts + " attempts)", last.retryAfter());
    }

    private PluginResult<JsonNode> attempt(URI uri, Duration timeout) throws InterruptedException {
        HttpTransport.Response response;
        try {
            response = transport.get(uri, HEADERS, timeout, MAX_RESPONSE_BYTES);
        } catch (HttpTimeoutException e) {
            return PluginResult.failed(FailureKind.TRANSIENT, "timeout after " + timeout + " for " + uri);
        } catch (IOException e) {
            return PluginResult.failed(FailureKind.TRANSIENT, "network error for " + uri + ": " + e.getClass().getSimpleName());
        }
        int status = response.status();
        if (status == 429) {
            return new PluginResult.Failed<>(FailureKind.RATE_LIMITED, "HTTP 429 from " + uri, retryAfter(response).orElse(null));
        }
        if (status >= 500) {
            return new PluginResult.Failed<>(FailureKind.TRANSIENT, "HTTP " + status + " from " + uri, retryAfter(response).orElse(null));
        }
        if (status == 404) {
            return PluginResult.failed(FailureKind.NOT_FOUND, "HTTP 404 from " + uri);
        }
        if (status == 401 || status == 403) {
            return PluginResult.failed(FailureKind.AUTH, "HTTP " + status + " from " + uri);
        }
        if (status < 200 || status >= 300) {
            return PluginResult.failed(FailureKind.PERMANENT, "HTTP " + status + " from " + uri);
        }
        if (response.truncated()) {
            return PluginResult.failed(FailureKind.PERMANENT, "response from " + uri + " exceeds " + MAX_RESPONSE_BYTES + " bytes");
        }
        try {
            return PluginResult.ok(json.readTree(response.body()));
        } catch (JacksonException e) {
            return PluginResult.failed(FailureKind.PARSE, "invalid JSON from " + uri);
        }
    }

    private static boolean retryable(FailureKind kind) {
        return kind == FailureKind.TRANSIENT || kind == FailureKind.RATE_LIMITED;
    }

    /** Exponential backoff with jitter; a server-requested Retry-After wins when longer (capped). */
    static Duration backoff(int attempt, Duration retryAfter) {
        long exponential = BASE_BACKOFF.toMillis() << Math.min(attempt - 1, 10);
        long jitter = ThreadLocalRandom.current().nextLong(0, BASE_BACKOFF.toMillis() / 2 + 1);
        Duration computed = Duration.ofMillis(Math.min(exponential + jitter, MAX_BACKOFF.toMillis()));
        if (retryAfter != null && retryAfter.compareTo(computed) > 0) {
            return retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter;
        }
        return computed;
    }

    /** Only the delta-seconds form of Retry-After is honoured; HTTP-date values are ignored (backoff applies). */
    static Optional<Duration> retryAfter(HttpTransport.Response response) {
        return response.header("Retry-After").flatMap(value -> {
            try {
                long seconds = Long.parseLong(value.strip());
                return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }
}
