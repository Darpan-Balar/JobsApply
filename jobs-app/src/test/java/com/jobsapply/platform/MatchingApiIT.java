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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

/** Candidate import → verification → matching over HTTP, as the local user. */
@ApiTest
class MatchingApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired ConfigImportService config;
    @Autowired DiscoveryFixture fixture;
    @Autowired ScriptedJobSourceAdapter source;

    @Test
    void importVerifyAndMatch() {
        config.importDirectory(Path.of("..", "config"), false);
        HttpApi api = new HttpApi(port);
        String key = uniqueKey("me");

        JsonNode imported = api.post("/api/v1/me/candidate-profiles/import", "application/yaml", CandidateYaml.profile(key)).json();
        assertThat(imported.get("outcomes").get(0).get("outcome").asString()).isEqualTo("CREATED");

        HttpApi.Response detail = api.get("/api/v1/me/candidate-profiles/" + key);
        assertThat(detail.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(detail.json().get("factsByVerification").get("UNVERIFIED").asInt()).isEqualTo(6);

        assertThat(api.postJson("/api/v1/me/candidate-profiles/" + key + "/verification", Map.of("all", true)).json()
                .get("versionNo").asInt()).isEqualTo(2);

        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(DiscoveryFixture.job("x", "Backend Engineer", "Berlin, Germany").toBuilder()
                .descriptionHtml("<p>Java and PostgreSQL.</p>").build())));
        api.postJson("/api/v1/sync?employer=" + board.employerKey(), Map.of());
        String postingId = api.get("/api/v1/postings?employer=" + board.employerKey()).json().get("items").get(0).get("id").asString();

        JsonNode evaluation = api.postJson("/api/v1/me/matches/evaluate",
                Map.of("postingId", postingId, "candidateProfile", key, "roleProfile", "backend-engineer")).json();
        assertThat(evaluation.get("outcome").asString()).isEqualTo("CREATED");
        String matchId = evaluation.get("match").get("id").asString();
        assertThat(evaluation.get("match").get("result").get("ruleOutcomes").size()).isEqualTo(4);

        JsonNode list = api.get("/api/v1/me/matches?candidateProfile=" + key + "&roleProfile=backend-engineer").json();
        assertThat(list.get("items").findValuesAsString("id")).contains(matchId);
        assertThat(api.get("/api/v1/me/matches/" + matchId).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/me/matches/" + UUID.randomUUID()).status()).isEqualTo(404);

        assertThat(api.get("/api/v1/role-profiles").json().findValuesAsString("key")).contains("backend-engineer", "quant-developer");
        assertThat(api.post("/api/v1/me/role-profiles/import", "application/yaml",
                "schemaVersion: 1\nkind: EmployerSet\nemployers: [{key: x, displayName: X}]\n").status()).isEqualTo(422);
    }
}
