package com.jobsapply.platform;

import static com.jobsapply.platform.support.DiscoveryFixture.job;
import static com.jobsapply.platform.support.ScriptedJobSourceAdapter.completePage;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.DiscoveryFixture;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.ScriptedJobSourceAdapter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

@ApiTest
class DiscoveryApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired DiscoveryFixture fixture;
    @Autowired ScriptedJobSourceAdapter source;

    @Test
    void syncRunsPostingsAndRecheckOverHttp() {
        HttpApi api = new HttpApi(port);
        DiscoveryFixture.Board board = fixture.board();
        source.script(board.externalRef(), List.of(completePage(job("a", "Backend Engineer"), job("b", "Data Engineer"))));

        JsonNode dryRun = api.postJson("/api/v1/sync?employer=" + board.employerKey() + "&dryRun=true", Map.of()).json();
        assertThat(dryRun.get(0).get("counts").get("inserted").asInt()).isEqualTo(2);
        assertThat(api.get("/api/v1/postings?employer=" + board.employerKey()).json().get("totalItems").asLong()).isZero();

        JsonNode reports = api.postJson("/api/v1/sync?employer=" + board.employerKey(), Map.of()).json();
        assertThat(reports.get(0).get("outcome").asString()).isEqualTo("SUCCEEDED");
        String runId = reports.get(0).get("syncRunId").asString();

        JsonNode run = api.get("/api/v1/sync-runs/" + runId).json();
        assertThat(run.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(run.get("employerKey").asString()).isEqualTo(board.employerKey());
        assertThat(api.get("/api/v1/sync-runs?employer=" + board.employerKey()).json().size()).isEqualTo(2);

        JsonNode page = api.get("/api/v1/postings?employer=" + board.employerKey() + "&q=backend").json();
        assertThat(page.get("totalItems").asLong()).isEqualTo(1);
        String postingId = page.get("items").get(0).get("id").asString();

        JsonNode detail = api.get("/api/v1/postings/" + postingId).json();
        assertThat(detail.get("descriptionText").asString()).isEqualTo("Build things.");
        assertThat(detail.get("listings").get(0).get("sourceKey").asString()).isEqualTo(board.sourceKey());

        JsonNode recheck = api.postJson("/api/v1/postings/" + postingId + "/availability-check", Map.of()).json();
        assertThat(recheck.get("checks").get(0).get("availability").asString())
                .as("unscripted check fails, so nothing is concluded").isEqualTo("UNKNOWN");
        assertThat(recheck.get("availability").asString()).isEqualTo("OPEN");

        assertThat(api.get("/api/v1/postings?availability=NOPE").status()).isEqualTo(400);
    }
}
