package com.jobsapply.platform.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal HTTP client for API tests against the real server: keeps the session cookie and sends the CSRF header the
 * way the dashboard does. {@code new HttpApi(port)} is signed in as the shared test administrator.
 */
public final class HttpApi {

    public record Response(int status, String body, java.net.http.HttpHeaders headers) {
        public JsonNode json() {
            return MAPPER.readTree(body);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
    private final String base;

    /** Signed in as {@link TestAccounts#ADMIN}. */
    public HttpApi(int port) {
        this(port, TestAccounts.ADMIN, TestAccounts.PASSWORD);
    }

    private HttpApi(int port, String username, String password) {
        this.base = "http://localhost:" + port;
        if (username != null) {
            Response login = signIn(username, password);
            if (login.status() != 200) {
                throw new IllegalStateException("test sign-in failed: " + login.status() + " " + login.body());
            }
        }
    }

    public static HttpApi anonymous(int port) {
        return new HttpApi(port, null, null);
    }

    public static HttpApi signedIn(int port, String username, String password) {
        return new HttpApi(port, username, password);
    }

    /** Fetches the session (which issues the CSRF cookie), then posts the sign-in form. */
    public Response signIn(String username, String password) {
        get("/api/v1/auth/session");
        return post("/api/v1/auth/login", "application/x-www-form-urlencoded",
                "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8));
    }

    public String csrfToken() {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue)
                .findFirst().orElse("");
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    public Response delete(String path) {
        return send(withCsrf(HttpRequest.newBuilder(URI.create(base + path)).DELETE()));
    }

    public Response post(String path, String contentType, String body) {
        return send(withCsrf(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))));
    }

    /** POST without the CSRF header (to prove it is required). */
    public Response postWithoutCsrf(String path, Object body) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))));
    }

    public Response postBytes(String path, String contentType, byte[] body) {
        return send(withCsrf(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))));
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
        return send(withCsrf(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header(headerName, headerValue)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))));
    }

    public Response putJson(String path, Object body) {
        return send(withCsrf(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))));
    }

    private HttpRequest.Builder withCsrf(HttpRequest.Builder request) {
        String token = csrfToken();
        if (token.isEmpty()) {
            get("/api/v1/auth/session");
            token = csrfToken();
        }
        return request.header("X-XSRF-TOKEN", token);
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
