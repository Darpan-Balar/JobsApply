package com.jobsapply.platform;

import static com.jobsapply.platform.support.TestData.uniqueKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobsapply.platform.employer.EmployerPreferenceService;
import com.jobsapply.platform.employer.EmployerPreferenceView;
import com.jobsapply.platform.employer.EmployerService;
import com.jobsapply.platform.employer.EmployerSpec;
import com.jobsapply.platform.employer.PreferenceMode;
import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.shared.error.MissingUserContextException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import com.jobsapply.platform.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Employer preferences are private per user (ADR-0002). */
@IntegrationTest
class EmployerPreferenceIT {

    @Autowired EmployerPreferenceService preferences;
    @Autowired EmployerService employers;
    @Autowired UserService users;

    @Test
    void eachUserHasTheirOwnPrivatePreferences() {
        UUID alice = users.createUser(NewUser.builder().username(uniqueKey("alice")).displayName("A").build()).id();
        UUID bob = users.createUser(NewUser.builder().username(uniqueKey("bob")).displayName("B").build()).id();
        String employer = uniqueKey("acme");
        employers.create(EmployerSpec.builder().key(employer).displayName("Acme").discoveryPriority(50).enabled(true).build(), List.of());

        UserContext.runAs(alice, () -> preferences.set(employer, PreferenceMode.WATCH, 90, "dream job"));
        UserContext.runAs(bob, () -> preferences.set(employer, PreferenceMode.IGNORE, 10, null));

        UserContext.runAs(alice, () -> assertThat(preferences.list()).containsExactly(
                new EmployerPreferenceView(employer, "Acme", PreferenceMode.WATCH, 90, "dream job")));
        UserContext.runAs(bob, () -> {
            assertThat(preferences.list()).extracting(EmployerPreferenceView::mode).containsExactly(PreferenceMode.IGNORE);
            preferences.remove(employer);
            assertThat(preferences.list()).isEmpty();
        });
        UserContext.runAs(alice, () -> assertThat(preferences.list()).as("bob's removal does not touch alice's").hasSize(1));
    }

    @Test
    void settingTwiceUpdatesInPlace() {
        UUID user = users.createUser(NewUser.builder().username(uniqueKey("u")).displayName("U").build()).id();
        String employer = uniqueKey("acme");
        employers.create(EmployerSpec.builder().key(employer).displayName("Acme").discoveryPriority(50).enabled(true).build(), List.of());

        UserContext.runAs(user, () -> {
            preferences.set(employer, PreferenceMode.WATCH, 50, null);
            preferences.set(employer, PreferenceMode.IGNORE, 20, "changed my mind");
            assertThat(preferences.list()).singleElement().satisfies(p -> assertThat(p.mode()).isEqualTo(PreferenceMode.IGNORE));
        });
    }

    @Test
    void requiresAUser() {
        assertThatThrownBy(() -> preferences.list()).isInstanceOf(MissingUserContextException.class);
    }
}
