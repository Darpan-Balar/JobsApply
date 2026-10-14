package com.jobsapply.adapters;

import com.jobsapply.adapters.http.HttpTransport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Scripted transport: per-URI queues of responses (the last one repeats). Records every request. */
public final class FakeTransport implements HttpTransport {

    private final Map<String, Deque<Object>> scripts = new HashMap<>();
    public final List<URI> requests = new ArrayList<>();

    public FakeTransport respond(String uri, int status, String body, Map<String, String> headers) {
        scripts.computeIfAbsent(uri, u -> new ArrayDeque<>())
                .add(new Response(status, body.getBytes(StandardCharsets.UTF_8), false, headers));
        return this;
    }

    public FakeTransport respond(String uri, int status, String body) {
        return respond(uri, status, body, Map.of());
    }

    public FakeTransport fixture(String uri, String resource) {
        return respond(uri, 200, read(resource));
    }

    public FakeTransport fail(String uri, IOException error) {
        scripts.computeIfAbsent(uri, u -> new ArrayDeque<>()).add(error);
        return this;
    }

    @Override
    public Response get(URI uri, Map<String, String> headers, Duration timeout, int maxBytes) throws IOException {
        requests.add(uri);
        Deque<Object> script = scripts.get(uri.toString());
        if (script == null || script.isEmpty()) {
            return new Response(404, "{}".getBytes(StandardCharsets.UTF_8), false, Map.of());
        }
        Object next = script.size() > 1 ? script.poll() : script.peek();
        if (next instanceof IOException e) {
            throw e;
        }
        Response response = (Response) next;
        return response.body().length > maxBytes ? new Response(response.status(), new byte[0], true, response.headers()) : response;
    }

    public static String read(String resource) {
        try {
            return Files.readString(Path.of("src/test/resources/fixtures", resource));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
