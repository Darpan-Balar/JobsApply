package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.plugin.PluginRegistry;
import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.SampleResumes;
import com.jobsapply.spi.common.PluginResult;
import com.jobsapply.spi.profile.FactRef;
import com.jobsapply.spi.render.Claim;
import com.jobsapply.spi.render.DocumentRenderer;
import com.jobsapply.spi.render.RenderOptions;
import com.jobsapply.spi.render.RenderedDocument;
import com.jobsapply.spi.render.ResumeDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A resume file becomes an unconfirmed draft profile; nothing is stored until the candidate saves it (ADR-0015). */
@ApiTest
class ResumeImportApiIT {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Autowired ConfigImportService config;
    @Autowired PluginRegistry<DocumentRenderer> renderers;

    HttpApi api;

    @BeforeEach
    void setUp() {
        config.importDirectory(Path.of("..", "config"), false);
        api = new HttpApi(port);
    }

    private JsonNode draft(String fileName, String contentType, byte[] body) {
        HttpApi.Response response = api.postBytes("/api/v1/me/candidate-profiles/drafts?fileName=" + fileName, contentType, body);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        return response.json();
    }

    @Test
    void onlyTheLocalExtractorIsOfferedWithoutAnApiKey() {
        JsonNode options = api.get("/api/v1/me/candidate-profiles/extractors").json();
        assertThat(options.findValuesAsString("id")).containsExactly("heuristic-resume-extractor");
        assertThat(options.get(0).get("sendsTextExternally").asBoolean()).isFalse();
    }

    @Test
    void aResumeBecomesAnUnconfirmedDraftMappedOntoTheCatalog() {
        JsonNode draft = draft("resume.txt", "text/plain", SampleResumes.CLASSIC.getBytes(StandardCharsets.UTF_8));
        JsonNode snapshot = draft.get("snapshot");

        assertThat(draft.get("sentExternally").asBoolean()).isFalse();
        assertThat(snapshot.get("contact").get("fullName").asString()).isEqualTo("Jane Example");
        assertThat(snapshot.get("contact").get("email").asString()).isEqualTo("jane.example@example.com");
        assertThat(snapshot.get("experiences").findValuesAsString("id")).containsExactly("exp-beta-ag", "exp-example-gmbh");
        assertThat(snapshot.get("experiences").get(0).get("start").asString()).isEqualTo("2023-01");
        assertThat(snapshot.get("experiences").get(0).get("skills").toString()).contains("kafka");
        assertThat(snapshot.get("achievements").get(0).get("experienceId").asString()).isEqualTo("exp-beta-ag");
        assertThat(snapshot.get("education").get(0).get("level").asString()).isEqualTo("BACHELOR");
        assertThat(snapshot.get("skills").findValuesAsString("skill"))
                .contains("java", "kotlin", "sql", "spring-boot", "docker", "kafka", "postgresql");
        assertThat(draft.get("unknownSkills").toString()).contains("pandas");
        assertThat(snapshot.findValuesAsString("verification")).isNotEmpty().allMatch("UNVERIFIED"::equals);
    }

    @Test
    void aRealPdfIsReadToo() {
        UUID version = UUID.randomUUID();
        ResumeDocument resume = new ResumeDocument("t", version, new ResumeDocument.Header("Jane Example", "jane@example.com", null, null, List.of()),
                List.of(new ResumeDocument.Section("Experience", List.of(
                        new Claim("Senior Backend Engineer | Beta AG", List.of(new FactRef(version, "x")), false),
                        new Claim("Jan 2023 – Present", List.of(new FactRef(version, "x")), false)))));
        byte[] pdf = ((PluginResult.Ok<RenderedDocument>) renderers.require("pdf-resume").render(resume, new RenderOptions("application/pdf", null)))
                .value().content();

        JsonNode snapshot = draft("cv.pdf", "application/pdf", pdf).get("snapshot");

        assertThat(snapshot.get("contact").get("fullName").asString()).isEqualTo("Jane Example");
        assertThat(snapshot.get("experiences").get(0).get("title").asString()).isEqualTo("Senior Backend Engineer");
        assertThat(snapshot.get("experiences").get(0).get("employer").asString()).isEqualTo("Beta AG");
    }

    @Test
    void nothingIsStoredUntilTheCandidateSavesTheReviewedDraft() {
        JsonNode draft = draft("resume.txt", "text/plain", SampleResumes.CLASSIC.getBytes(StandardCharsets.UTF_8));
        String key = uniqueKey("cv");
        assertThat(api.get("/api/v1/me/candidate-profiles").body()).doesNotContain(key);

        HttpApi.Response saved = api.putJson("/api/v1/me/candidate-profiles/" + key,
                Map.of("displayName", "From my CV", "snapshot", JSON.treeToValue(draft.get("snapshot"), Object.class), "note", "from resume"));

        assertThat(saved.status()).as(saved.body()).isEqualTo(200);
        JsonNode detail = api.get("/api/v1/me/candidate-profiles/" + key).json();
        assertThat(detail.get("factsByVerification").get("UNVERIFIED").asInt()).isPositive();
        assertThat(detail.get("factsByVerification").has("VERIFIED")).as("nothing is confirmed by the import").isFalse();
    }

    @Test
    void unreadableOrForeignFilesAreRefused() {
        assertThat(api.postBytes("/api/v1/me/candidate-profiles/drafts?fileName=photo.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'})
                .status()).isEqualTo(409);
        assertThat(api.postBytes("/api/v1/me/candidate-profiles/drafts?fileName=cv.pdf", "application/pdf", "%PDF-1.7 broken".getBytes())
                .json().get("detail").asString()).contains("could not be read");
        assertThat(HttpApi.anonymous(port).postBytes("/api/v1/me/candidate-profiles/drafts", "text/plain", "x".getBytes()).status()).isEqualTo(401);
    }
}
