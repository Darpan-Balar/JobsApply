package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.identity.UserRole;
import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.TestAccounts;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** The administrator's view of the platform, and health details only for administrators. */
@ApiTest
class SystemApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void administratorsSeeTheSystemAndRunJobs() {
        HttpApi admin = new HttpApi(port);
        JsonNode system = admin.get("/api/v1/admin/system").json();
        assertThat(system.get("schemaVersion").asString()).isEqualTo("14");
        assertThat(system.get("scheduledSyncEnabled").asBoolean()).isFalse();
        assertThat(admin.postJson("/api/v1/admin/system/retention", Map.of()).json().get("ran").asBoolean()).isTrue();

        String username = uniqueKey("u");
        accounts.account(username, UserRole.USER);
        HttpApi user = HttpApi.signedIn(port, username, TestAccounts.PASSWORD);
        assertThat(user.get("/api/v1/admin/system").status()).isEqualTo(403);
        assertThat(user.postJson("/api/v1/admin/system/sync", Map.of()).status()).isEqualTo(403);
    }

    @Test
    void metricsAreForAdministratorsAndCountSignIns() {
        HttpApi admin = new HttpApi(port);
        JsonNode signins = admin.get("/actuator/metrics/jobs.auth.signins?tag=result:success").json();
        assertThat(signins.get("measurements").get(0).get("value").asDouble()).isPositive();

        String username = uniqueKey("m");
        accounts.account(username, UserRole.USER);
        assertThat(HttpApi.signedIn(port, username, TestAccounts.PASSWORD).get("/actuator/metrics").status()).isEqualTo(403);
        assertThat(HttpApi.anonymous(port).get("/actuator/metrics").status()).isEqualTo(401);
    }

    @Test
    void healthDetailsAreForAdministratorsAndAnUndeliveredBacklogIsAWarning() {
        JsonNode anonymous = HttpApi.anonymous(port).get("/actuator/health").json();
        assertThat(anonymous.has("components")).isFalse();

        JsonNode health = new HttpApi(port).get("/actuator/health").json();
        assertThat(health.get("components").get("encryption").get("status").asString()).isEqualTo("UP");
        assertThat(health.get("components").get("documentStore").get("status").asString()).isEqualTo("UP");
        assertThat(health.get("components").get("db").get("status").asString()).isEqualTo("UP");

        UUID stuck = UUID.randomUUID();
        jdbc.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date) "
                + "values (?, 'test', 'java.lang.String', '\"x\"', now() - interval '1 hour')", stuck);
        try {
            HttpApi.Response response = new HttpApi(port).get("/actuator/health");
            assertThat(response.status()).as("still serving").isEqualTo(200);
            assertThat(response.json().get("components").get("eventPublications").get("status").asString()).isEqualTo("WARNING");
            assertThat(response.json().get("status").asString()).isEqualTo("WARNING");
        } finally {
            jdbc.update("delete from event_publication where id = ?", stuck);
        }
    }
}
