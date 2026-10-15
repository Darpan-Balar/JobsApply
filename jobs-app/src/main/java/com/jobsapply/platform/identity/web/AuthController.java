package com.jobsapply.platform.identity.web;

import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserSummary;
import com.jobsapply.platform.identity.internal.AccountPrincipal;
import com.jobsapply.platform.identity.internal.SecurityConfiguration;
import com.jobsapply.platform.identity.internal.SessionView;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session, one-time setup and password change. Signing in and out are {@code POST /api/v1/auth/login} (form fields
 * {@code username}, {@code password}) and {@code POST /api/v1/auth/logout}; both need the CSRF header.
 */
@Tag(name = "Authentication", description = "Session, first-account setup, sign-in, password")
@RestController
@RequiredArgsConstructor
class AuthController {

    private final UserService users;

    record Setup(@NotBlank String username, @NotBlank @Size(max = 200) String displayName, @NotNull String password) {
        @Override
        public String toString() {
            return "Setup[" + username + ", password redacted]";
        }
    }

    record PasswordChange(@NotNull String currentPassword, @NotNull String newPassword) {
        @Override
        public String toString() {
            return "PasswordChange[redacted]";
        }
    }

    @Operation(summary = "Who is signed in, and whether the one-time setup is needed (also issues the CSRF cookie)")
    @GetMapping("/api/v1/auth/session")
    SessionView session(@Parameter(hidden = true) Authentication authentication, @Parameter(hidden = true) CsrfToken csrf,
            HttpServletRequest request) {
        csrf.getToken();
        boolean setupRequired = users.setupRequired();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            UserSummary user = users.get(principal.userId());
            return new SessionView(new SessionView.Me(user.id(), user.username(), user.displayName(), user.role()), setupRequired, false);
        }
        return new SessionView(null, setupRequired, setupRequired && SecurityConfiguration.fromThisMachine(request));
    }

    @Operation(summary = "Create the first account (ADMIN); only from this machine and only once",
            description = "Takes over the pre-login single-user account if it exists, so its data is kept. Sign in afterwards.")
    @PostMapping("/api/v1/auth/setup")
    @ResponseStatus(HttpStatus.CREATED)
    UserSummary setup(@Valid @RequestBody Setup body, HttpServletRequest request) {
        if (!SecurityConfiguration.fromThisMachine(request)) {
            throw new DomainRuleViolationException("Setup is only possible from the machine the platform runs on");
        }
        return users.setUpFirstAccount(body.username(), body.displayName(), body.password());
    }

    @PostMapping("/api/v1/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody PasswordChange body) {
        users.changePassword(UserContext.requireUserId(), body.currentPassword(), body.newPassword());
    }
}
