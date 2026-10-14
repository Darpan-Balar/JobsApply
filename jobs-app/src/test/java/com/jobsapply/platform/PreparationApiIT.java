package com.jobsapply.platform;

import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static com.jobsapply.platform.support.TestData.uniqueKey;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

@ApiTest
class PreparationApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired ConfigImportService config;
    @Autowired DiscoveryFixture fixture;
    @Autowired ScriptedJobSourceAdapter source;

    @Test
    void prepareReviewAndDownloadOverHttp() {
        config.importDirectory(Path.of("..", "config"), false);
        HttpApi api = new HttpApi(port);
        String key = uniqueKey("me");
        api.post("/api/v1/me/candidate-profiles/import?trust=true", "application/yaml", CandidateYaml.profile(key));

        byte[] master = new byte[300];
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, master, 0, 9);
        for (int i = 9; i < master.length; i++) {
            master[i] = (byte) (i * 31);
        }
        JsonNode uploaded = api.postBytes("/api/v1/me/candidate-profiles/" + key + "/master-resumes?fileName=cv.pdf", "application/pdf", master).json();
        assertThat(api.getBytes("/api/v1/me/candidate-profiles/" + key + "/master-resumes/" + uploaded.get("id").asString() + "/content"))
                .as("binary upload round-trips byte for byte").isEqualTo(master);

        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Backend Engineer", "Berlin, Germany").toBuilder()
                .descriptionHtml("<p>Java and Kafka.</p>").build())));
        source.availability(board.externalRef(), "x", PluginResult.ok(new AvailabilityObservation(Availability.OPEN, true, "200", Instant.now())));
        api.postJson("/api/v1/sync?employer=" + board.employerKey(), Map.of());
        String postingId = api.get("/api/v1/postings?employer=" + board.employerKey()).json().get("items").get(0).get("id").asString();

        JsonNode prepared = api.postJson("/api/v1/me/applications/prepare", Map.of("postingId", postingId, "candidateProfile", key,
                "roleProfile", "backend-engineer", "additionalQuestions", List.of(Map.of("text", "Do you need sponsorship?", "required", true)))).json();
        assertThat(prepared.get("status").asString()).isEqualTo("READY_FOR_REVIEW");
        String applicationId = prepared.get("applicationId").asString();

        HttpApi.Response detail = api.get("/api/v1/me/applications/" + applicationId);
        assertThat(detail.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        JsonNode content = detail.json().get("currentPackage").get("content");
        String pdfId = content.get("documents").findValuesAsString("id").get(content.get("documents").findValuesAsString("mediaType").indexOf("application/pdf"));
        HttpApi.Response pdf = api.get("/api/v1/me/applications/" + applicationId + "/documents/" + pdfId);
        assertThat(pdf.headers().firstValue("Content-Type")).contains("application/pdf");
        assertThat(pdf.headers().firstValue("X-Content-Type-Options")).contains("nosniff");

        String questionId = content.get("answers").get(0).get("question").get("id").asString();
        assertThat(api.putJson("/api/v1/me/applications/" + applicationId + "/answers/" + questionId, Map.of("answer", "No")).json()
                .get("packageVersion").asInt()).isEqualTo(2);
        assertThat(api.get("/api/v1/me/applications").json().findValuesAsString("id")).contains(applicationId);
        assertThat(api.putJson("/api/v1/me/applications/" + applicationId + "/resume", Map.of("sections", List.of(Map.of("heading", "X",
                "claims", List.of(Map.of("text", "Invented 99 patents", "factIds", List.of("exp-acme"), "userApproved", false)))))).status())
                .isEqualTo(409);
    }
}
