package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.NewUser;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Ensures the configured local user exists (idempotent). */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class LocalUserBootstrap implements ApplicationRunner {

    private final IdentityProperties properties;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        IdentityProperties.LocalUser local = properties.localUser();
        UserSummary user = userService.ensureUser(NewUser.builder()
                .username(local.username())
                .displayName(local.displayName())
                .build());
        log.info("Local user ready: id={}", user.id());
    }
}
