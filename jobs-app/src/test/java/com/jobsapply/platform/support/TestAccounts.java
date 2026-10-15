package com.jobsapply.platform.support;

import com.jobsapply.platform.identity.NewAccount;
import com.jobsapply.platform.identity.UserRole;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserSummary;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import org.springframework.beans.factory.InitializingBean;

/** Ensures the shared test administrator exists (API tests sign in as it). Test-only credentials. */
public class TestAccounts implements InitializingBean {

    public static final String ADMIN = "api-admin";
    public static final String PASSWORD = "test-only-password-1";

    private final UserService users;

    public TestAccounts(UserService users) {
        this.users = users;
    }

    @Override
    public void afterPropertiesSet() {
        account(ADMIN, UserRole.ADMIN);
    }

    /** An account with {@link #PASSWORD}, created if absent. */
    public UserSummary account(String username, UserRole role) {
        return users.findByUsername(username).filter(UserSummary::canSignIn).orElseGet(() -> {
            try {
                return users.createAccount(NewAccount.builder().username(username).displayName(username).password(PASSWORD).role(role).build());
            } catch (DomainRuleViolationException raced) {
                return users.findByUsername(username).orElseThrow(() -> raced);
            }
        });
    }
}
