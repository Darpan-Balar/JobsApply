package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.identity.NewAccount;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserRole;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserStatus;
import com.jobsapply.platform.identity.UserSummary;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.support.IntegrationTest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class IdentityIT {

    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onboardingAnotherUserIsOneCall() {
        String username = uniqueKey("new");
        UserSummary created = users.createUser(NewUser.builder().username(username).displayName("New User").email("New@Example.com").build());

        assertThat(created.email()).isEqualTo("new@example.com");
        assertThat(created.canSignIn()).isFalse();
        assertThat(users.findByUsername(username)).contains(created);
    }

    @Test
    void usernamesAndEmailsAreUnique() {
        String username = uniqueKey("dup");
        users.createUser(NewUser.builder().username(username).displayName("A").build());
        assertThatThrownBy(() -> users.createUser(NewUser.builder().username(username).displayName("B").build()))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void ensureUserIsIdempotent() {
        NewUser request = NewUser.builder().username(uniqueKey("idem")).displayName("Idem").build();
        assertThat(users.ensureUser(request).id()).isEqualTo(users.ensureUser(request).id());
    }

    @Test
    void invalidUsernameIsRejectedBeforeReachingTheDatabase() {
        assertThatThrownBy(() -> users.createUser(NewUser.builder().username("Bad Name").displayName("x").build()))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void passwordsAreHashedAndMustBeLongEnough() {
        String username = uniqueKey("pw");
        assertThatThrownBy(() -> users.createAccount(account(username, "short-pw"))).isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("at least 12");
        assertThatThrownBy(() -> users.createAccount(account(username, "x".repeat(73)))).hasMessageContaining("at most 72 bytes");
        assertThatThrownBy(() -> users.createAccount(account(username, username + "-and-more"))).hasMessageContaining("username");

        UserSummary created = users.createAccount(account(username, "correct horse battery"));
        assertThat(created.canSignIn()).isTrue();
        String stored = jdbc.queryForObject("select password_hash from app_user where id = ?", String.class, created.id());
        assertThat(stored).startsWith("{bcrypt}").doesNotContain("correct horse");
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() {
        UserSummary user = users.createAccount(account(uniqueKey("chg"), "first password 123"));
        assertThatThrownBy(() -> users.changePassword(user.id(), "wrong password!!", "second password 123"))
                .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("current password");
        users.changePassword(user.id(), "first password 123", "second password 123");
        assertThatThrownBy(() -> users.changePassword(user.id(), "first password 123", "third password 123"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void youCannotDisableYourself() {
        UserSummary admin = users.createAccount(account(uniqueKey("adm"), "admin password 123"));
        assertThatThrownBy(() -> users.disable(admin.id(), admin.id())).isInstanceOf(DomainRuleViolationException.class);
        UserSummary other = users.createUser(NewUser.builder().username(uniqueKey("o")).displayName("O").build());
        assertThat(users.disable(other.id(), admin.id()).status()).isEqualTo(UserStatus.DISABLED);
    }

    /**
     * The one-time setup needs a database where no account can sign in. The shared test database has accounts, so
     * their credentials are set aside for the duration of this test and restored afterwards.
     */
    @Test
    void theFirstAccountTakesOverTheLegacyUserOnceAndBecomesAdmin() {
        List<Map<String, Object>> saved = jdbc.queryForList(
                "select id, username, password_hash, password_changed_at, role from app_user where password_hash is not null");
        UUID legacy = users.findByUsername("local").map(UserSummary::id)
                .orElseGet(() -> users.createUser(NewUser.builder().username("local").displayName("Local User").build()).id());
        try {
            jdbc.update("update app_user set password_hash = null, password_changed_at = null");
            assertThat(users.setupRequired()).isTrue();
            assertThatThrownBy(() -> users.setUpFirstAccount("owner", "Owner", "short")).isInstanceOf(DomainRuleViolationException.class);
            assertThat(users.setupRequired()).isTrue();

            UserSummary owner = users.setUpFirstAccount("owner-" + UUID.randomUUID().toString().substring(0, 6), "The Owner",
                    "a long enough password");

            assertThat(owner.id()).as("the legacy account and its data are kept").isEqualTo(legacy);
            assertThat(owner.role()).isEqualTo(UserRole.ADMIN);
            assertThat(owner.canSignIn()).isTrue();
            assertThat(users.setupRequired()).isFalse();
            assertThatThrownBy(() -> users.setUpFirstAccount("intruder", "Intruder", "another long password"))
                    .isInstanceOf(DomainRuleViolationException.class).hasMessageContaining("already complete");
        } finally {
            jdbc.update("update app_user set username = 'local', role = 'USER', password_hash = null, password_changed_at = null where id = ?", legacy);
            for (Map<String, Object> row : saved) {
                jdbc.update("update app_user set username = ?, password_hash = ?, password_changed_at = ?, role = ? where id = ?",
                        row.get("username"), row.get("password_hash"), row.get("password_changed_at"), row.get("role"), row.get("id"));
            }
        }
    }

    private static NewAccount account(String username, String password) {
        return NewAccount.builder().username(username).displayName(username).password(password).role(UserRole.USER).build();
    }
}
