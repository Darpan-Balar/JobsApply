package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.configimport.ConfigImportService;
import com.jobsapply.platform.identity.UserRole;
import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.CandidateYaml;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.TestAccounts;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

/** The data export contains everything of the requesting user and nothing of anyone else (ADR-0014). */
@ApiTest
class ExportApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired ConfigImportService config;

    private HttpApi user() {
        String username = uniqueKey("ex");
        accounts.account(username, UserRole.USER);
        return HttpApi.signedIn(port, username, TestAccounts.PASSWORD);
    }

    @Test
    void anExportHoldsTheUsersOwnDataOnly() {
        config.importDirectory(Path.of("..", "config"), false);
        HttpApi alice = user();
        HttpApi bob = user();
        String profile = uniqueKey("me");
        String contact = "Rita " + uniqueKey("r");
        alice.post("/api/v1/me/candidate-profiles/import?trust=true", "application/yaml", CandidateYaml.profile(profile));
        alice.postJson("/api/v1/me/contacts", Map.of("name", contact));
        alice.postJson("/api/v1/me/follow-ups", Map.of("dueOn", "2030-01-01", "note", "Call back"));

        HttpApi.Response response = alice.get("/api/v1/me/export");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition")).hasValueSatisfying(v -> assertThat(v).contains("attachment",
                "jobs-export-"));
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        JsonNode export = response.json();
        assertThat(export.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(export.get("candidateProfiles").get(0).get("detail").get("snapshot").get("contact").get("fullName").asString())
                .isEqualTo("Jane Example");
        assertThat(export.get("tracking").get("contacts").findValuesAsString("name")).containsExactly(contact);
        assertThat(export.get("tracking").get("followUps").get(0).get("note").asString()).isEqualTo("Call back");

        String bobs = bob.get("/api/v1/me/export").body();
        assertThat(bobs).doesNotContain(contact, profile, "Jane Example", "Call back");
        assertThat(HttpApi.anonymous(port).get("/api/v1/me/export").status()).isEqualTo(401);
    }
}
