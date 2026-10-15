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

/** Sign-in, CSRF, roles, lockout and per-user isolation over real HTTP (ADR-0013). */
@ApiTest
class SecurityApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired TestAccounts accounts;
    @Autowired ConfigImportService config;

    private HttpApi as(UserRole role) {
        String username = uniqueKey(role == UserRole.ADMIN ? "adm" : "usr");
        accounts.account(username, role);
        return HttpApi.signedIn(port, username, TestAccounts.PASSWORD);
    }

    @Test
    void everythingPersonalOrCatalogNeedsSignIn() {
        HttpApi anonymous = HttpApi.anonymous(port);
        assertThat(anonymous.get("/api/v1/me/applications").status()).isEqualTo(401);
        assertThat(anonymous.get("/api/v1/me/candidate-profiles").status()).isEqualTo(401);
        assertThat(anonymous.get("/api/v1/postings").status()).isEqualTo(401);
        assertThat(anonymous.get("/api/v1/employers").status()).isEqualTo(401);
        assertThat(anonymous.get("/api/v1/me/applications").json().get("title").asString()).isEqualTo("Unauthenticated");
        assertThat(anonymous.get("/actuator/health").status()).isEqualTo(200);

        JsonNode session = anonymous.get("/api/v1/auth/session").json();
        assertThat(session.get("user").isNull()).isTrue();
        assertThat(session.get("setupRequired").asBoolean()).isFalse();
        assertThat(anonymous.csrfToken()).isNotEmpty();
    }

    @Test
    void wrongCredentialsGetOneGenericAnswerAndRepeatedFailuresLockTheUsername() {
        String username = uniqueKey("lock");
        accounts.account(username, UserRole.USER);
        HttpApi client = HttpApi.anonymous(port);

        HttpApi.Response unknown = client.signIn("nobody-" + username, "whatever password");
        HttpApi.Response wrong = client.signIn(username, "not the password");
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.json().get("detail").asString()).isEqualTo(unknown.json().get("detail").asString());

        for (int i = 0; i < 4; i++) {
            client.signIn(username, "not the password");
        }
        assertThat(client.signIn(username, TestAccounts.PASSWORD).status()).as("locked even with the right password").isEqualTo(429);
    }

    @Test
    void writesNeedTheCsrfTokenAndSignInRotatesTheSession() {
        HttpApi client = HttpApi.anonymous(port);
        client.get("/api/v1/auth/session");
        assertThat(client.postWithoutCsrf("/api/v1/auth/login", Map.of()).status()).as("sign-in itself is CSRF-protected").isEqualTo(403);

        HttpApi user = as(UserRole.USER);
        assertThat(user.postWithoutCsrf("/api/v1/me/matches/run", Map.of()).status()).isEqualTo(403);
        assertThat(user.get("/api/v1/me/applications").status()).isEqualTo(200);
    }

    @Test
    void onlyAdministratorsChangeTheSharedCatalogOrManageAccounts() {
        HttpApi user = as(UserRole.USER);
        HttpApi admin = as(UserRole.ADMIN);
        Map<String, Object> employer = Map.of("key", uniqueKey("emp"), "displayName", "Example Corp");

        assertThat(user.get("/api/v1/employers").status()).isEqualTo(200);
        assertThat(user.postJson("/api/v1/employers", employer).status()).isEqualTo(403);
        assertThat(user.postJson("/api/v1/sync", Map.of()).status()).isEqualTo(403);
        assertThat(user.get("/api/v1/admin/users").status()).isEqualTo(403);
        assertThat(admin.postJson("/api/v1/employers", employer).status()).isEqualTo(201);
        assertThat(admin.get("/api/v1/admin/users").status()).isEqualTo(200);
    }

    @Test
    void personalDataStaysWithItsOwnerEvenForAdministrators() {
        HttpApi owner = as(UserRole.USER);
        HttpApi admin = as(UserRole.ADMIN);
        config.importDirectory(Path.of("..", "config"), false);
        String key = uniqueKey("me");
        assertThat(owner.post("/api/v1/me/candidate-profiles/import?trust=true", "application/yaml", CandidateYaml.profile(key)).status())
                .isLessThan(300);

        assertThat(owner.get("/api/v1/me/candidate-profiles/" + key).status()).isEqualTo(200);
        assertThat(admin.get("/api/v1/me/candidate-profiles/" + key).status()).isEqualTo(404);
        assertThat(admin.get("/api/v1/me/candidate-profiles").body()).doesNotContain(key);
    }

    @Test
    void signingOutEndsTheSession() {
        HttpApi user = as(UserRole.USER);
        assertThat(user.post("/api/v1/auth/logout", "application/json", "").status()).isEqualTo(204);
        assertThat(user.get("/api/v1/me/applications").status()).isEqualTo(401);
    }

    @Test
    void changingThePasswordOverHttp() {
        String username = uniqueKey("pwd");
        accounts.account(username, UserRole.USER);
        HttpApi user = HttpApi.signedIn(port, username, TestAccounts.PASSWORD);

        assertThat(user.postJson("/api/v1/me/password", Map.of("currentPassword", "wrong", "newPassword", "a brand new password")).status())
                .isEqualTo(409);
        assertThat(user.postJson("/api/v1/me/password", Map.of("currentPassword", TestAccounts.PASSWORD, "newPassword", "a brand new password"))
                .status()).isEqualTo(204);
        assertThat(HttpApi.anonymous(port).signIn(username, TestAccounts.PASSWORD).status()).isEqualTo(401);
        assertThat(HttpApi.anonymous(port).signIn(username, "a brand new password").status()).isEqualTo(200);
    }

    @Test
    void administratorsOnboardAndDisableAccounts() {
        HttpApi admin = as(UserRole.ADMIN);
        String username = uniqueKey("new");
        JsonNode created = admin.postJson("/api/v1/admin/users", Map.of("username", username, "displayName", "New Person",
                "password", "their first password")).json();
        assertThat(created.get("role").asString()).isEqualTo("USER");
        assertThat(created.has("passwordHash")).isFalse();

        HttpApi newcomer = HttpApi.signedIn(port, username, "their first password");
        assertThat(newcomer.get("/api/v1/me/applications").status()).isEqualTo(200);

        assertThat(admin.postJson("/api/v1/admin/users/" + created.get("id").asString() + "/disable", Map.of()).status()).isEqualTo(200);
        assertThat(newcomer.get("/api/v1/me/applications").status()).as("an open session ends").isEqualTo(401);
        assertThat(HttpApi.anonymous(port).signIn(username, "their first password").status()).isEqualTo(401);
    }

    @Test
    void setupIsRefusedOnceAnAccountExists() {
        HttpApi anonymous = HttpApi.anonymous(port);
        assertThat(anonymous.postJson("/api/v1/auth/setup", Map.of("username", "late", "displayName", "Late",
                "password", "a long enough password")).status()).isEqualTo(409);
    }

    @Test
    void securityHeadersAreSet() {
        HttpApi.Response response = HttpApi.anonymous(port).get("/api/v1/auth/session");
        assertThat(response.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(v -> assertThat(v).contains("frame-ancestors 'none'"));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
    }
}
