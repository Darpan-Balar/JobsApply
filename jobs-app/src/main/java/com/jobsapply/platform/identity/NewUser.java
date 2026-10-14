package com.jobsapply.platform.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record NewUser(
        @NotBlank @Pattern(regexp = USERNAME_REGEX, message = "must be 2-64 chars: lower-case letters, digits, '.', '_' or '-'")
                String username,
        @NotBlank @Size(max = 200) String displayName,
        @Email @Size(max = 320) String email) {

    public static final String USERNAME_REGEX = "^[a-z0-9][a-z0-9._-]{1,63}$";
}
