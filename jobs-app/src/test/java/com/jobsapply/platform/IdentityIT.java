package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserStatus;
import com.jobsapply.platform.identity.UserSummary;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.support.IntegrationTest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class IdentityIT {

    @Autowired UserService users;

    @Test
    void configuredLocalUserIsCreatedOnStartup() {
        assertThat(users.findByUsername("local")).isPresent().get()
                .satisfies(u -> assertThat(u.status()).isEqualTo(UserStatus.ACTIVE));
    }

    @Test
    void onboardingAnotherUserIsOneCall() {
        String username = uniqueKey("new");
        UserSummary created = users.createUser(NewUser.builder().username(username).displayName("New User").email("New@Example.com").build());

        assertThat(created.email()).isEqualTo("new@example.com");
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
}
