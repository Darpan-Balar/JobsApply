package com.jobsapply.browser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.spi.application.ApplicationDestination;
import com.jobsapply.spi.application.ApplicationSession;
import com.jobsapply.spi.application.Blocker;
import com.jobsapply.spi.application.ConfirmationEvidence;
import com.jobsapply.spi.application.FieldMappingPlan;
import com.jobsapply.spi.application.FieldMappingPlan.Attachment;
import com.jobsapply.spi.application.FieldMappingPlan.Confidence;
import com.jobsapply.spi.application.FieldMappingPlan.FieldMapping;
import com.jobsapply.spi.application.FillReport;
import com.jobsapply.spi.application.FormInspection;
import com.jobsapply.spi.common.FailureKind;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.common.ValidationReport;
import com.jobsapply.spi.render.ApplicationQuestion;
import com.jobsapply.spi.render.ApplicationQuestion.QuestionType;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The adapter against real headless Chromium and local fixture forms. The test plays the human where one is needed. */
class PlaywrightApplicationAdapterIT {

    static HttpServer server;
    static final AtomicInteger POSTS = new AtomicInteger();
    static PlaywrightApplicationAdapter adapter;
    static final byte[] PDF = "%PDF-1.7\nresume".getBytes(StandardCharsets.US_ASCII);

    final List<byte[]> artifacts = new ArrayList<>();
    Session session;

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            byte[] body;
            if (path.equals("/submit") && exchange.getRequestMethod().equals("POST")) {
                POSTS.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Location", "/submitted.html");
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            }
            if (path.equals("/forbidden")) {
                status = 403;
                body = "<html><body>Forbidden</body></html>".getBytes(StandardCharsets.UTF_8);
            } else {
                try (InputStream in = PlaywrightApplicationAdapterIT.class.getResourceAsStream("/forms" + path)) {
                    if (in == null) {
                        status = 404;
                        body = "not found".getBytes(StandardCharsets.UTF_8);
                    } else {
                        body = in.readAllBytes();
                    }
                }
            }
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        adapter = new PlaywrightApplicationAdapter(new BrowserSettings(true, null, Duration.ofSeconds(20), Duration.ofSeconds(5), null),
                Clock.systemUTC());
    }

    @AfterAll
    static void stop() {
        adapter.close();
        server.stop(0);
    }

    @AfterEach
    void closeWindow() {
        if (session != null) {
            adapter.close(session);
        }
    }

    private FormInspection open(String path) {
        session = new Session(UUID.randomUUID(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path), artifacts);
        return ok(adapter.open(session));
    }

    private static <T> T ok(PluginResult<T> result) {
        assertThat(result).isInstanceOf(PluginResult.Ok.class);
        return ((PluginResult.Ok<T>) result).value();
    }

    private static FieldMapping high(String field, String value) {
        return new FieldMapping(field, value, null, Confidence.HIGH, List.of(), "test");
    }

    private String valueOf(String selector) {
        String[] value = new String[1];
        adapter.actAsHuman(session.id(), page -> value[0] = String.valueOf(page.evaluate(
                "s => { const e = document.querySelector(s); return e.type === 'file' ? Array.from(e.files).map(f => f.name).join(',') "
                        + ": e.type === 'checkbox' || e.type === 'radio' ? String(e.checked) : e.value; }", selector)));
        return value[0];
    }

    @Test
    void inspectsLabelsTypesRequiredFlagsAndOptions() {
        FormInspection form = open("/apply.html");

        assertThat(form.blockers()).isEmpty();
        assertThat(form.fields()).extracting(ApplicationQuestion::id).containsExactly("first_name", "last_name", "email", "phone",
                "preferred_name", "resume", "question_1", "question_3", "cover_letter_text", "consent", "location", "question_2");
        assertThat(form.fields()).filteredOn(f -> f.id().equals("first_name")).singleElement().satisfies(f -> {
            assertThat(f.text()).isEqualTo("First Name");
            assertThat(f.required()).isTrue();
        });
        assertThat(form.fields()).filteredOn(f -> f.id().equals("question_1")).singleElement().satisfies(f -> {
            assertThat(f.type()).isEqualTo(QuestionType.SINGLE_SELECT);
            assertThat(f.options()).containsExactly("Job board", "Friend");
        });
        assertThat(form.fields()).filteredOn(f -> f.id().equals("question_2")).singleElement().satisfies(f -> {
            assertThat(f.text()).isEqualTo("Will you require visa sponsorship?");
            assertThat(f.options()).containsExactly("Yes", "No");
            assertThat(f.required()).isTrue();
        });
        assertThat(form.fields()).filteredOn(f -> f.id().equals("resume")).singleElement().extracting(ApplicationQuestion::type)
                .isEqualTo(QuestionType.FILE);
        assertThat(form.fields()).filteredOn(f -> f.id().equals("location")).singleElement().satisfies(f -> {
            assertThat(f.type()).isEqualTo(QuestionType.SINGLE_SELECT);
            assertThat(f.options()).isEmpty();
        });
    }

    @Test
    void fillsOnlyHighConfidenceEmptyFieldsAndNeverSubmits() {
        open("/apply.html");
        int postsBefore = POSTS.get();
        FieldMappingPlan plan = new FieldMappingPlan(List.of(
                high("first_name", "Jane"), high("email", "jane@example.org"),
                new FieldMapping("last_name", "Doe", null, Confidence.MEDIUM, List.of(), "test"),
                high("preferred_name", "Overwritten"),
                high("question_1", "friend"), high("question_2", "No"), high("question_3", "7"),
                new FieldMapping("resume", null, new Attachment("doc-1", "resume.pdf", "application/pdf", PDF), Confidence.HIGH, List.of(), "test"),
                high("location", "Berlin"), high("unknown_field", "x")));

        FillReport report = ok(adapter.fill(session, plan));

        assertThat(report.filledFieldIds()).containsExactlyInAnyOrder("first_name", "email", "question_1", "question_2", "question_3", "resume");
        assertThat(report.skippedFieldIds()).containsExactlyInAnyOrder("last_name", "preferred_name", "location", "unknown_field");
        assertThat(valueOf("#first_name")).isEqualTo("Jane");
        assertThat(valueOf("#last_name")).as("MEDIUM is never filled").isEmpty();
        assertThat(valueOf("#preferred_name")).as("the human's input wins").isEqualTo("Typed by human");
        assertThat(valueOf("#question_1")).isEqualTo("b");
        assertThat(valueOf("input[name=question_2][value='0']")).isEqualTo("true");
        assertThat(valueOf("#resume")).isEqualTo("resume.pdf");
        assertThat(valueOf("#consent")).as("consent is untouched").isEqualTo("false");
        assertThat(POSTS.get()).as("nothing was submitted").isEqualTo(postsBefore);

        ValidationReport validation = ok(adapter.validate(session));
        assertThat(validation.issues()).extracting(ValidationReport.Issue::path).containsExactlyInAnyOrder("last_name", "consent");
    }

    @Test
    void anOptionTheFormDoesNotOfferIsSkipped() {
        open("/apply.html");
        FillReport report = ok(adapter.fill(session, new FieldMappingPlan(List.of(high("question_1", "Newspaper"), high("question_2", "Maybe")))));
        assertThat(report.filledFieldIds()).isEmpty();
        assertThat(report.skippedFieldIds()).containsExactlyInAnyOrder("question_1", "question_2");
    }

    @Test
    void formsInsideFramesAreFound() {
        FormInspection form = open("/embedded.html");
        assertThat(form.fields()).extracting(ApplicationQuestion::id).contains("first_name", "email", "question_2");
        assertThat(ok(adapter.fill(session, new FieldMappingPlan(List.of(high("email", "jane@example.org"))))).filledFieldIds())
                .containsExactly("email");
    }

    @Test
    void loginWallsOneTimeCodesChallengesAndRefusalsStopAssistance() {
        assertThat(open("/login.html").blockers()).containsExactly(Blocker.LOGIN_REQUIRED);
        FillReport report = ok(adapter.fill(session, new FieldMappingPlan(List.of(high("u", "jane@example.org")))));
        assertThat(report.blockers()).containsExactly(Blocker.LOGIN_REQUIRED);
        assertThat(report.filledFieldIds()).isEmpty();
        adapter.close(session);

        assertThat(open("/otp.html").blockers()).containsExactly(Blocker.MFA);
        adapter.close(session);
        assertThat(open("/challenge.html").blockers()).containsExactly(Blocker.CAPTCHA);
        adapter.close(session);
        assertThat(open("/forbidden").blockers()).contains(Blocker.ACCESS_DENIED);
        adapter.close(session);
        assertThat(open("/job-page.html").blockers()).containsExactly(Blocker.UNRECOGNIZED_FORM);
    }

    @Test
    void aMissingPageIsADefiniteFailure() {
        session = new Session(UUID.randomUUID(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/gone.html"), artifacts);
        assertThat(adapter.open(session)).isInstanceOfSatisfying(PluginResult.Failed.class,
                f -> assertThat(f.kind()).isEqualTo(FailureKind.PERMANENT));
    }

    @Test
    void aCaptchaInTheFormIsLeftToTheHuman() {
        FormInspection form = open("/captcha-form.html");
        assertThat(form.blockers()).isEmpty();
        assertThat(form.humanSteps()).singleElement().asString().contains("CAPTCHA");
        assertThat(ok(adapter.fill(session, new FieldMappingPlan(List.of(high("email", "jane@example.org"))))).filledFieldIds())
                .containsExactly("email");
    }

    @Test
    void theConfirmationAfterTheHumanSubmitsIsEvidence() {
        open("/apply.html");
        int before = POSTS.get();
        adapter.actAsHuman(session.id(), page -> {
            page.evaluate("() => document.querySelector('form').noValidate = true");
            page.click("button[type=submit]");
            page.waitForURL("**/submitted.html");
        });

        ConfirmationEvidence evidence = ok(adapter.observeConfirmation(session, Duration.ofSeconds(5)));

        assertThat(POSTS.get()).isEqualTo(before + 1);
        assertThat(evidence.kind()).isEqualTo(ConfirmationEvidence.Kind.CONFIRMATION_PAGE);
        assertThat(evidence.detail()).isEqualTo("Application submitted");
        assertThat(evidence.pageUrl().getPath()).isEqualTo("/submitted.html");
        assertThat(evidence.artifactRef()).isEqualTo("artifact-0");
        assertThat(artifacts).singleElement().satisfies(png -> assertThat(png).startsWith(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
    }

    @Test
    void thankYouTextNextToAnUnsubmittedFormIsNotEvidence() {
        open("/banner.html");
        ConfirmationEvidence evidence = ok(adapter.observeConfirmation(session, Duration.ofSeconds(2)));
        assertThat(evidence.kind()).isEqualTo(ConfirmationEvidence.Kind.NONE);
        assertThat(artifacts).isEmpty();
    }

    @Test
    void aWindowTheHumanClosedIsReportedAndEverythingElseFailsCleanly() {
        open("/apply.html");
        adapter.actAsHuman(session.id(), page -> page.close());

        assertThat(ok(adapter.observeConfirmation(session, Duration.ofSeconds(2))).detail()).contains("closed");
        assertThat(adapter.fill(session, new FieldMappingPlan(List.of(high("email", "x"))))).isInstanceOf(PluginResult.Failed.class);
        adapter.close(session);
        adapter.close(session);
    }

    record Session(UUID id, URI url, List<byte[]> artifacts) implements ApplicationSession {

        @Override
        public UUID applicationId() {
            return id;
        }

        @Override
        public ApplicationDestination destination() {
            return new ApplicationDestination(url, "test");
        }

        @Override
        public String saveArtifact(String mediaType, byte[] content) {
            artifacts.add(content);
            return "artifact-" + (artifacts.size() - 1);
        }
    }

}
