package com.jobsapply.platform.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal HTTP client for API tests against the real server. */
public final class HttpApi {

    public record Response(int status, String body, java.net.http.HttpHeaders headers) {
        public JsonNode json() {
            return MAPPER.readTree(body);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    public HttpApi(int port) {
        this.base = "http://localhost:" + port;
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    public Response delete(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).DELETE());
    }

    public Response post(String path, String contentType, String body) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    public Response postBytes(String path, String contentType, byte[] body) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    /** GET returning raw bytes (downloads). */
    public byte[] getBytes(String path) {
        try {
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public Response postJson(String path, Object body) {
        return post(path, "application/json", MAPPER.writeValueAsString(body));
    }

    public Response postJson(String path, Object body, String headerName, String headerValue) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header(headerName, headerValue)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))));
    }

    public Response putJson(String path, Object body) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))));
    }

    private Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
