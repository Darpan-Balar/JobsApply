package com.jobsapply.adapters.http;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Sends one GET request. Replaceable so adapter tests run against fixtures without network access. */
public interface HttpTransport {

    /**
     * @param maxBytes the body is read up to this many bytes; larger bodies yield {@link Response#truncated()}
     * @throws IOException on network failure or timeout
     */
    Response get(URI uri, Map<String, String> headers, Duration timeout, int maxBytes) throws IOException, InterruptedException;

    record Response(int status, byte[] body, boolean truncated, Map<String, String> headers) {

        public Optional<String> header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }
    }
}
