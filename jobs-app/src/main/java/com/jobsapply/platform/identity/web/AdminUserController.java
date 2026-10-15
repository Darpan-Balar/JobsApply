package com.jobsapply.platform.identity.web;

import com.jobsapply.platform.identity.NewAccount;
import com.jobsapply.platform.identity.UserRole;
import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserSummary;
import com.jobsapply.platform.shared.tenancy.UserContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Accounts (ADMIN only). Administrators manage sign-in, never other users' personal data. */
@Tag(name = "Accounts (admin)", description = "Onboard people, reset passwords, disable accounts")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
class AdminUserController {

    private final UserService users;

    record CreateAccount(@NotBlank String username, @NotBlank @Size(max = 200) String displayName, @Size(max = 320) String email,
            @NotNull String password, UserRole role) {
        @Override
        public String toString() {
            return "CreateAccount[" + username + ", password redacted]";
        }
    }

    record PasswordReset(@NotNull String newPassword) {
        @Override
        public String toString() {
            return "PasswordReset[redacted]";
        }
    }

    @GetMapping
    List<UserSummary> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserSummary create(@RequestBody CreateAccount body) {
        return users.createAccount(NewAccount.builder().username(body.username()).displayName(body.displayName()).email(body.email())
                .password(body.password()).role(body.role() == null ? UserRole.USER : body.role()).build());
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable UUID id, @RequestBody PasswordReset body) {
        users.resetPassword(id, body.newPassword());
    }

    @PostMapping("/{id}/disable")
    UserSummary disable(@PathVariable UUID id) {
        return users.disable(id, UserContext.requireUserId());
    }
}
