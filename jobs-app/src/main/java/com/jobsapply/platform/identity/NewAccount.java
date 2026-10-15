package com.jobsapply.platform.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** An account that can sign in. The password is checked against {@link Passwords} before it is hashed. */
@Builder
public record NewAccount(
        @NotBlank @Pattern(regexp = NewUser.USERNAME_REGEX, message = "must be 2-64 chars: lower-case letters, digits, '.', '_' or '-'")
                String username,
        @NotBlank @Size(max = 200) String displayName,
        @Email @Size(max = 320) String email,
        @NotNull String password,
        @NotNull UserRole role) {

    @Override
    public String toString() {
        return "NewAccount[" + username + ", " + role + ", password redacted]";
    }
}
