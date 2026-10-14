package com.jobsapply.adapters.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link HttpTransport} on the JDK HTTP client. Redirects within HTTPS are followed; plain HTTP is never used. */
public final class JdkHttpTransport implements HttpTransport {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public Response get(URI uri, Map<String, String> headers, Duration timeout, int maxBytes)
            throws IOException, InterruptedException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS is allowed: " + uri);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout).GET();
        headers.forEach(request::header);
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            byte[] body = in.readNBytes(maxBytes + 1);
            boolean truncated = body.length > maxBytes;
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) -> {
                List<String> v = values;
                if (!v.isEmpty()) {
                    responseHeaders.put(name, v.getFirst());
                }
            });
            return new Response(response.statusCode(), truncated ? new byte[0] : body, truncated, responseHeaders);
        }
    }
}
