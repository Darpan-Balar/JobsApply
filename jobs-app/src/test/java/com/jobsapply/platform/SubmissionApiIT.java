package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.TestData.uniqueKey;
import static com.jobsapply.platform.support.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import com.jobsapply.spi.common.Availability;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.source.AvailabilityObservation;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

@ApiTest
class SubmissionApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired ConfigImportService config;
    @Autowired DiscoveryFixture fixture;
    @Autowired ScriptedJobSourceAdapter source;

    @Test
    void approveSubmitOnceAndRecordTheOutcomeOverHttp() {
        config.importDirectory(Path.of("..", "config"), false);
        HttpApi api = new HttpApi(port);
        String candidate = uniqueKey("me");
        api.post("/api/v1/me/candidate-profiles/import?trust=true", "application/yaml", CandidateYaml.profile(candidate));
        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Backend Engineer", "Berlin, Germany").toBuilder()
                .descriptionHtml("<p>Java and Kafka.</p>").build())));
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.OPEN, true, "200", Instant.now())));
        api.postJson("/api/v1/sync?employer=" + board.employerKey(), Map.of());
        String postingId = api.get("/api/v1/postings?employer=" + board.employerKey()).json().get("items").get(0).get("id").asString();
        String id = api.postJson("/api/v1/me/applications/prepare", Map.of("postingId", postingId, "candidateProfile", candidate,
                "roleProfile", "backend-engineer")).json().get("applicationId").asString();
        String base = "/api/v1/me/applications/" + id;
        String packageId = api.get(base).json().get("currentPackage").get("id").asString();

        assertThat(api.get(base + "/approval/blockers").json().isEmpty()).isTrue();
        assertThat(api.postJson(base + "/approval", Map.of()).status()).isEqualTo(400);
        JsonNode approval = api.postJson(base + "/approval", Map.of("packageId", packageId)).json();
        assertThat(approval.get("consumedAt").isNull()).isTrue();

        assertThat(api.postJson(base + "/submission", Map.of()).status()).as("Idempotency-Key is required").isEqualTo(400);
        assertThat(api.postJson(base + "/submission", Map.of(), "Idempotency-Key", "bad key!").status()).isEqualTo(400);
        String key = uniqueKey("idem-key");
        HttpApi.Response started = api.postJson(base + "/submission", Map.of(), "Idempotency-Key", key);
        assertThat(started.status()).isEqualTo(200);
        assertThat(started.json().get("applicationUrl").asString()).isEqualTo("https://jobs.example/x/apply");
        String attemptId = started.json().get("attempt").get("id").asString();
        assertThat(api.postJson(base + "/submission", Map.of(), "Idempotency-Key", key).json().get("attempt").get("id").asString())
                .isEqualTo(attemptId);

        assertThat(api.postJson(base + "/submission/" + attemptId + "/outcome", Map.of("outcome", "CONFIRMED")).status()).isEqualTo(409);
        JsonNode confirmed = api.postJson(base + "/submission/" + attemptId + "/outcome", Map.of("outcome", "CONFIRMED",
                "evidence", Map.of("kind", "CONFIRMATION_EMAIL", "detail", "Your application was received"))).json();
        assertThat(confirmed.get("outcome").asString()).isEqualTo("CONFIRMED");
        assertThat(confirmed.has("credible")).isFalse();
        assertThat(api.get(base).json().get("application").get("status").asString()).isEqualTo("SUBMITTED");
        assertThat(api.get(base + "/attempts").json().size()).isEqualTo(1);

        eventually(() -> assertThat(api.get("/api/v1/me/audit?aggregateId=" + id).json().findValuesAsString("eventType"))
                .contains("approval-granted", "submission-started", "submission-finished"));
        assertThat(api.postJson(base + "/withdrawal", Map.of()).json().get("status").asString()).isEqualTo("WITHDRAWN");
    }
}
