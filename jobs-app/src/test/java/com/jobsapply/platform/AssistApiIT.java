package com.jobsapply.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobsapply.platform.support.ApiTest;
import com.jobsapply.platform.support.HttpApi;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

/** HTTP contract of browser assistance (the flow itself is covered by AssistIT). */
@ApiTest
class AssistApiIT {

    @Value("${local.server.port}")
    int port;

    @Test
    void requestsAreValidatedAndUnknownApplicationsAreNotFound() {
        HttpApi api = new HttpApi(port);
        String base = "/api/v1/me/applications/" + UUID.randomUUID();
        assertThat(api.postJson(base + "/assist", Map.of()).status()).as("Idempotency-Key is required").isEqualTo(400);
        assertThat(api.postJson(base + "/assist", Map.of(), "Idempotency-Key", "bad key!").status()).isEqualTo(400);
        assertThat(api.postJson(base + "/assist/confirmation?waitSeconds=0", Map.of()).status()).isEqualTo(400);
        assertThat(api.postJson(base + "/assist/confirmation?waitSeconds=601", Map.of()).status()).isEqualTo(400);
        assertThat(api.get(base + "/assist").status()).isEqualTo(404);
        assertThat(api.postJson(base + "/assist", Map.of(), "Idempotency-Key", "valid-key-123").status()).isEqualTo(404);
        assertThat(api.get(base + "/attempts/" + UUID.randomUUID() + "/screenshot").status()).isEqualTo(404);
    }
}
