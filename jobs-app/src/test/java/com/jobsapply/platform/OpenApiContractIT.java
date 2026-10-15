package com.jobsapply.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.HttpApi;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * API contract between the server and the dashboard: the dashboard's TypeScript types are generated from
 * {@code jobs-web/src/api/openapi.json}, so that snapshot must equal what the server publishes. After an intended API
 * change, refresh it with {@code -Dopenapi.update=true} and rebuild the dashboard (its type check then shows every
 * place the change affects).
 */
@ApiTest
class OpenApiContractIT {

    static final Path SNAPSHOT = Path.of("..", "jobs-web", "src", "api", "openapi.json");
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Value("${local.server.port}")
    int port;

    @Test
    void theDashboardIsBuiltAgainstThePublishedApi() throws IOException {
        JsonNode live = HttpApi.anonymous(port).get("/v3/api-docs").json();
        ((tools.jackson.databind.node.ObjectNode) live).remove("servers");
        String published = canonical(live);
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(SNAPSHOT, published);
        }
        assertThat(Files.exists(SNAPSHOT)).as("missing %s; run with -Dopenapi.update=true", SNAPSHOT).isTrue();
        assertThat(canonical(JSON.readTree(Files.readString(SNAPSHOT))))
                .as("the API changed; refresh the snapshot with -Dopenapi.update=true and rebuild jobs-web")
                .isEqualTo(published);
    }

    private static String canonical(JsonNode node) {
        Object tree = JSON.treeToValue(node, Object.class);
        return JSON.writeValueAsString(tree) + "\n";
    }

}
