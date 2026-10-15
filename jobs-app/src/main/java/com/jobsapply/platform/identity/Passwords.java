package com.jobsapply.platform.identity;

import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import java.nio.charset.StandardCharsets;

/**
 * Password rules: at least 12 characters (length matters more than composition), at most 72 bytes — BCrypt ignores
 * anything beyond, so longer passwords are refused rather than silently truncated.
 */
public final class Passwords {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private Passwords() {}

    public static void requireAcceptable(String password, String username) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new DomainRuleViolationException("The password must have at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new DomainRuleViolationException("The password must be at most " + MAX_BYTES + " bytes");
        }
        if (password.isBlank() || (username != null && password.toLowerCase().contains(username.toLowerCase()))) {
            throw new DomainRuleViolationException("The password must not contain the username");
        }
    }
}
