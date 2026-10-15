package com.jobsapply.llm.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.resume.ResumeExtraction;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The Claude extractor against a local stand-in for the Messages API: no real calls, no key, no cost. */
class ClaudeResumeExtractorTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    HttpServer server;
    final AtomicReference<String> requestBody = new AtomicReference<>();
    final AtomicReference<com.sun.net.httpserver.Headers> requestHeaders = new AtomicReference<>();
    volatile int status = 200;
    volatile String responseBody;
    ClaudeResumeExtractor extractor;
    AnthropicClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestHeaders.set(exchange.getRequestHeaders());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("retry-after", "0");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        AnthropicSettings settings = new AnthropicSettings("test-key", null, null, Duration.ofSeconds(10),
                "http://127.0.0.1:" + server.getAddress().getPort());
        client = AnthropicOkHttpClient.builder().apiKey(settings.apiKey()).baseUrl(settings.baseUrl()).timeout(settings.timeout()).maxRetries(0).build();
        extractor = new ClaudeResumeExtractor(client, settings);
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    void sendsAConstrainedRequestAndReadsTheStructuredAnswer() {
        String answer = """
                {"fullName":"Jane Example","city":"Berlin","countryCode":"DE",
                 "positions":[{"title":"Senior Backend Engineer","employer":"Beta AG","location":"Berlin","start":"2023-01","end":null,
                   "current":true,"highlights":["Built an event pipeline on Kafka."]},
                   {"title":"Software Engineer","employer":"Example GmbH","location":null,"start":"2019","end":"2022-12","current":false,
                   "highlights":[]}],
                 "education":[{"institution":"Example University","degree":"B.Sc.","field":"Computer Science","start":null,"end":"2018-07",
                   "completed":true}],
                 "skills":["Java","Kafka"],"notes":[]}""";
        responseBody = minimalMessage("end_turn", answer);

        PluginResult<ResumeExtraction> result = extractor.extract("[email] · [phone]\nSenior Backend Engineer, Beta AG, Jan 2023 – Present");

        assertThat(result).isInstanceOf(PluginResult.Ok.class);
        ResumeExtraction r = ((PluginResult.Ok<ResumeExtraction>) result).value();
        assertThat(r.contact().fullName()).isEqualTo("Jane Example");
        assertThat(r.contact().countryCode()).isEqualTo("DE");
        assertThat(r.contact().email()).as("contact details are read locally, never from the model").isNull();
        assertThat(r.experiences()).hasSize(2);
        assertThat(r.experiences().get(0).start()).isEqualTo(YearMonth.of(2023, 1));
        assertThat(r.experiences().get(0).current()).isTrue();
        assertThat(r.experiences().get(1).start()).as("a bare year is not turned into a month").isNull();
        assertThat(r.notes()).anySatisfy(n -> assertThat(n).contains("Example GmbH", "2019"));
        assertThat(r.education().getFirst().completed()).isTrue();
        assertThat(r.skills()).containsExactly("Java", "Kafka");

        JsonNode request = JSON.readTree(requestBody.get());
        assertThat(request.path("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(request.path("output_config").path("effort").asString()).isEqualTo("low");
        assertThat(request.path("output_config").path("format").path("type").asString()).isEqualTo("json_schema");
        assertThat(request.path("output_config").path("format").path("schema").path("additionalProperties").asBoolean()).isFalse();
        assertThat(request.path("fallbacks").asString()).isEqualTo("default");
        assertThat(request.path("messages").get(0).toString()).contains("<resume>", "[email]");
        assertThat(requestHeaders.get().getFirst("x-api-key")).isEqualTo("test-key");
        assertThat(requestHeaders.get().getFirst("anthropic-version")).isNotBlank();
        assertThat(requestHeaders.get().getFirst("anthropic-beta")).contains("server-side-fallback-2026-07-01");
    }

    @Test
    void aRefusalOrAnOverloadIsAFailureNotAnEmptyProfile() {
        responseBody = minimalMessage("refusal", "");
        assertThat(extractor.extract("some resume text")).isInstanceOfSatisfying(PluginResult.Failed.class,
                f -> assertThat(f.message()).contains("declined"));

        status = 429;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}";
        assertThat(extractor.extract("some resume text")).isInstanceOfSatisfying(PluginResult.Failed.class,
                f -> assertThat(f.kind()).isEqualTo(FailureKind.TRANSIENT));

        status = 401;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        assertThat(extractor.extract("some resume text")).isInstanceOfSatisfying(PluginResult.Failed.class,
                f -> assertThat(f.message()).contains("401").doesNotContain("some resume text"));
    }

    private static String minimalMessage(String stopReason, String text) {
        return JSON.writeValueAsString(JSON.createObjectNode()
                .put("id", "msg_test").put("type", "message").put("role", "assistant").put("model", "claude-opus-5-5")
                .put("stop_reason", stopReason).putNull("stop_sequence")
                .<tools.jackson.databind.node.ObjectNode>set("content", JSON.createArrayNode().add(JSON.createObjectNode().put("type", "text").put("text", text)))
                .set("usage", JSON.createObjectNode().put("input_tokens", 10).put("output_tokens", 20)));
    }
}
