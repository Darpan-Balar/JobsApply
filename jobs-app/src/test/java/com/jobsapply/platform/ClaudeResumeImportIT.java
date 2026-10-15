package com.jobsapply.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.SampleResumes;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * With an API key configured, Claude is offered and used on request. A local stand-in for Anthropic's API proves what
 * leaves the machine: the resume text with the name, e-mail, phone number and links masked (ADR-0015).
 */
@ApiTest
class ClaudeResumeImportIT {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final AtomicReference<String> SENT = new AtomicReference<>();
    static final HttpServer API = stubApi();

    static HttpServer stubApi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/messages", exchange -> {
                SENT.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String answer = """
                        {"fullName":null,"city":"Berlin","countryCode":"DE",
                         "positions":[{"title":"Staff Engineer","employer":"Beta AG","location":"Berlin","start":"2023-01","end":null,
                           "current":true,"highlights":["Built an event pipeline on Kafka processing 2M events per day."]}],
                         "education":[],"skills":["Java"],"notes":[]}""";
                byte[] body = JSON.writeValueAsString(JSON.createObjectNode().put("id", "msg_1").put("type", "message").put("role", "assistant")
                        .put("model", "claude-opus-5-5").put("stop_reason", "end_turn").putNull("stop_sequence")
                        .<tools.jackson.databind.node.ObjectNode>set("content", JSON.createArrayNode().add(JSON.createObjectNode().put("type", "text").put("text", answer)))
                        .set("usage", JSON.createObjectNode().put("input_tokens", 1).put("output_tokens", 1))).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void claude(DynamicPropertyRegistry registry) {
        registry.add("jobs.llm.anthropic.api-key", () -> "test-key");
        registry.add("jobs.llm.anthropic.base-url", () -> "http://127.0.0.1:" + API.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        API.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired ConfigImportService config;

    @Test
    void claudeIsOfferedAndReceivesTheResumeWithIdentifiersMasked() {
        config.importDirectory(Path.of("..", "config"), false);
        HttpApi api = new HttpApi(port);

        JsonNode options = api.get("/api/v1/me/candidate-profiles/extractors").json();
        assertThat(options.findValuesAsString("id")).containsExactly("heuristic-resume-extractor", "claude-resume-extractor");
        assertThat(options.get(1).get("sendsTextExternally").asBoolean()).isTrue();

        HttpApi.Response response = api.postBytes("/api/v1/me/candidate-profiles/drafts?fileName=resume.txt&extractor=claude-resume-extractor",
                "text/plain", SampleResumes.CLASSIC.getBytes(StandardCharsets.UTF_8));

        assertThat(response.status()).as(response.body()).isEqualTo(200);
        String sent = SENT.get();
        assertThat(sent).doesNotContain("jane.example@example.com", "1234 5678", "linkedin.com/in/jane-example", "Jane Example", "JANE EXAMPLE");
        assertThat(sent).contains("[email]", "[phone]", "[link]", "[name]", "Beta AG", "Jan 2023");
        JsonNode draft = response.json();
        assertThat(draft.get("sentExternally").asBoolean()).isTrue();
        assertThat(draft.get("snapshot").get("experiences").get(0).get("title").asString()).as("Claude's structure is used").isEqualTo("Staff Engineer");
        assertThat(draft.get("snapshot").get("contact").get("email").asString()).as("contact details come from the local reading")
                .isEqualTo("jane.example@example.com");
        assertThat(draft.get("snapshot").get("contact").get("fullName").asString()).isEqualTo("Jane Example");
        assertThat(draft.get("snapshot").get("contact").get("countryCode").asString()).isEqualTo("DE");
    }
}
