package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.HttpApi;
import com.jobsapply.platform.support.HttpApi.Response;
import com.jobsapply.platform.support.TestJobSourceAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

/** The REST API end to end: real HTTP, local-user filter, problem details, CSV/YAML exchange. */
@ApiTest
class EmployerApiIT {

    @Value("${local.server.port}")
    int port;

    HttpApi api;
    String source;
    String tag;

    @BeforeEach
    void setUp() {
        api = new HttpApi(port);
        source = uniqueKey("src");
        tag = uniqueKey("tag");
        Response put = api.putJson("/api/v1/sources/" + source,
                Map.of("adapterId", TestJobSourceAdapter.ID, "displayName", "Test source", "enabled", true,
                        "settings", Map.of("requestTimeout", "PT5S")));
        assertThat(put.status()).isEqualTo(200);
    }

    private Map<String, Object> employer(String key, int priority) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", key);
        body.put("displayName", "Employer " + key);
        body.put("website", "https://" + key + ".example");
        body.put("tags", List.of(tag));
        body.put("discoveryPriority", priority);
        body.put("bindings", List.of(Map.of("sourceKey", source, "externalRef", key)));
        return body;
    }

    @Test
    void createReadUpdateArchive() {
        String key = uniqueKey("acme");
        Response created = api.postJson("/api/v1/employers", employer(key, 70));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).contains("/api/v1/employers/" + key);
        JsonNode body = created.json();
        assertThat(body.get("bindings").get(0).get("adapterId").asString()).isEqualTo(TestJobSourceAdapter.ID);
        long version = body.get("version").asLong();

        Map<String, Object> update = new LinkedHashMap<>(employer(key, 75));
        update.remove("key");
        update.remove("bindings");
        update.put("enabled", true);
        update.put("version", version);
        Response updated = api.putJson("/api/v1/employers/" + key, update);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.json().get("discoveryPriority").asInt()).isEqualTo(75);

        Response stale = api.putJson("/api/v1/employers/" + key, update);
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.json().get("title").asString()).isEqualTo("Concurrent modification");

        assertThat(api.postJson("/api/v1/employers/" + key + "/archive", Map.of()).json().get("archivedAt").isNull()).isFalse();
        assertThat(api.get("/api/v1/employers?tag=" + tag).json().get("totalItems").asLong()).isZero();
    }

    @Test
    void validationErrorsAreProblemDetailsWithFieldErrors() {
        Map<String, Object> bad = employer("Not A Key", 500);
        bad.put("website", "ftp://x");
        Response response = api.postJson("/api/v1/employers", bad);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(ct -> assertThat(ct).contains("application/problem+json"));
        assertThat(response.json().get("errors").findValuesAsString("field")).containsExactly("discoveryPriority", "key", "website");
    }

    @Test
    void notFoundAndConflictsAreReported() {
        assertThat(api.get("/api/v1/employers/" + uniqueKey("missing")).status()).isEqualTo(404);

        String owner = uniqueKey("owner");
        api.postJson("/api/v1/employers", employer(owner, 50));
        String other = uniqueKey("other");
        Map<String, Object> clash = employer(other, 50);
        clash.put("bindings", List.of(Map.of("sourceKey", source, "externalRef", owner)));
        Response response = api.postJson("/api/v1/employers", clash);
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.json().get("detail").asString()).isEqualTo("'" + owner + "' on " + source + " is already bound to employer '" + owner + "'");
        assertThat(api.get("/api/v1/employers/" + other).status()).as("the failed create was rolled back").isEqualTo(404);
    }

    @Test
    void searchSupportsFiltersSortingAndPaging() {
        for (int priority : new int[] {30, 90, 60}) {
            api.postJson("/api/v1/employers", employer(uniqueKey("s" + priority), priority));
        }
        JsonNode page = api.get("/api/v1/employers?tag=" + tag + "&minPriority=50&sort=PRIORITY&size=1&page=0").json();
        assertThat(page.get("totalItems").asLong()).isEqualTo(2);
        assertThat(page.get("items").get(0).get("discoveryPriority").asInt()).isEqualTo(90);
        assertThat(api.get("/api/v1/employers?tag=" + tag + "&adapter=" + TestJobSourceAdapter.ID).json().get("totalItems").asLong()).isEqualTo(3);
        assertThat(api.get("/api/v1/employers?size=1000").status()).isEqualTo(400);
        assertThat(api.get("/api/v1/employers?sort=password").status()).isEqualTo(400);
    }

    @Test
    void bindingsCanBeReconfiguredAndDisabled() {
        String key = uniqueKey("bind");
        api.postJson("/api/v1/employers", employer(key, 50));

        Response put = api.putJson("/api/v1/employers/" + key + "/bindings/" + source + "/" + key + "-2", Map.of("priority", 80));
        assertThat(put.status()).isEqualTo(200);
        assertThat(put.json().get("priority").asInt()).isEqualTo(80);
        assertThat(api.delete("/api/v1/employers/" + key + "/bindings/" + source + "/" + key).status()).isEqualTo(204);

        JsonNode bindings = api.get("/api/v1/employers/" + key).json().get("bindings");
        assertThat(bindings.findValuesAsString("externalRef")).containsExactly(key + "-2", key);
        assertThat(bindings.get(1).get("enabled").asBoolean()).isFalse();
    }

    @Test
    void preferencesUseTheLocalUser() {
        String key = uniqueKey("pref");
        api.postJson("/api/v1/employers", employer(key, 50));

        Response put = api.putJson("/api/v1/me/employer-preferences/" + key, Map.of("mode", "WATCH", "priority", 95));
        assertThat(put.status()).isEqualTo(200);
        assertThat(api.get("/api/v1/me/employer-preferences").json().findValuesAsString("employerKey")).contains(key);
        assertThat(api.delete("/api/v1/me/employer-preferences/" + key).status()).isEqualTo(204);
        assertThat(api.putJson("/api/v1/me/employer-preferences/" + key, Map.of()).status()).as("mode is required").isEqualTo(400);
    }

    @Test
    void enablingASourceRequiresAnInstalledAdapterAndValidSettings() {
        Response missingAdapter = api.putJson("/api/v1/sources/" + uniqueKey("src"),
                Map.of("adapterId", "greenhouse-job-board", "displayName", "GH", "enabled", true));
        assertThat(missingAdapter.status()).isEqualTo(409);
        assertThat(missingAdapter.json().get("detail").asString()).contains("adapter 'greenhouse-job-board' is not installed");

        Response badSettings = api.putJson("/api/v1/sources/" + uniqueKey("src"),
                Map.of("adapterId", TestJobSourceAdapter.ID, "displayName", "T", "enabled", true, "settings", Map.of("maxRetries", 50)));
        assertThat(badSettings.status()).isEqualTo(409);
        assertThat(badSettings.json().get("detail").asString()).contains("settings.maxRetries", "settings.requestTimeout");

        JsonNode adapters = api.get("/api/v1/plugins/job-source-adapters").json();
        assertThat(adapters.findValuesAsString("id")).contains(TestJobSourceAdapter.ID);
        assertThat(api.get("/api/v1/sources/" + source).json().get("adapterInstalled").asBoolean()).isTrue();
    }

    @Test
    void csvImportValidatesByRowAndSupportsDryRun() {
        String good = uniqueKey("csv");
        String csv = "key,displayName,tags,discoveryPriority,sources\n"
                + good + ",CSV Co," + tag + ",55," + source + ":" + good + "\n";

        Response dryRun = api.post("/api/v1/exchange/employers?dryRun=true", "text/csv", csv);
        assertThat(dryRun.status()).isEqualTo(200);
        assertThat(dryRun.json().get("outcomes").get(0).get("outcome").asString()).isEqualTo("CREATED");
        assertThat(api.get("/api/v1/employers/" + good).status()).as("dry run writes nothing").isEqualTo(404);

        assertThat(api.post("/api/v1/exchange/employers", "text/csv", csv).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/employers/" + good).json().get("discoveryPriority").asInt()).isEqualTo(55);
        assertThat(api.post("/api/v1/exchange/employers", "text/csv", csv).json().get("outcomes").get(0).get("outcome").asString())
                .isEqualTo("UNCHANGED");

        String bad = "key,displayName,website,sources\nok-" + good + ",OK,,\nBAD KEY,Bad,ftp://x,unknown-source:x\n";
        Response invalid = api.post("/api/v1/exchange/employers", "text/csv", bad);
        assertThat(invalid.status()).isEqualTo(422);
        assertThat(invalid.json().get("issues").findValuesAsString("path")).containsExactly("row 3.key", "row 3.website");
        assertThat(api.get("/api/v1/employers/ok-" + good).status()).as("all-or-nothing").isEqualTo(404);
    }

    @Test
    void yamlExportReimportsWithoutChanges() {
        String key = uniqueKey("yaml");
        api.postJson("/api/v1/employers", employer(key, 65));

        Response export = api.get("/api/v1/exchange/employers?format=YAML");
        assertThat(export.status()).isEqualTo(200);
        assertThat(export.body()).startsWith("---").contains("kind: \"EmployerSet\"", "key: \"" + key + "\"");

        Response reimport = api.post("/api/v1/config/documents?dryRun=true&fileName=export.yaml", "application/yaml", export.body());
        assertThat(reimport.status()).isEqualTo(200);
        assertThat(reimport.json().get("outcomes").findValuesAsString("outcome")).isNotEmpty().allMatch("UNCHANGED"::equals);
    }

    @Test
    void csvExportIsDownloadable() {
        String key = uniqueKey("dl");
        api.postJson("/api/v1/employers", employer(key, 50));
        Response export = api.get("/api/v1/exchange/employers?format=CSV");
        assertThat(export.headers().firstValue("Content-Disposition")).contains("attachment; filename=employers.csv");
        assertThat(export.body()).startsWith("key,displayName,").contains(key + ",Employer " + key);
    }
}
