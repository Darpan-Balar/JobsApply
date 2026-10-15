package com.jobsapply.platform.identity;

import com.jobsapply.platform.identity.internal.AppUser;
import com.jobsapply.platform.identity.internal.AppUserRepository;
import com.jobsapply.platform.identity.internal.IdentityProperties;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.NotFoundException;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

/** Public API of the identity module: users, accounts and passwords (ADR-0013). */
@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository users;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final Clock clock;

    /** Creates a user without a password (it cannot sign in until one is set). Fails if the username or email is taken. */
    public UserSummary createUser(@Valid NewUser request) {
        return save(newEntity(request.username(), request.displayName(), request.email(), UserRole.USER));
    }

    /** Creates an account that can sign in (an administrator onboarding someone). */
    public UserSummary createAccount(@Valid NewAccount request) {
        Passwords.requireAcceptable(request.password(), request.username());
        AppUser user = newEntity(request.username(), request.displayName(), request.email(), request.role());
        user.setPassword(passwordEncoder.encode(request.password()), Instant.now(clock));
        return save(user);
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findByUsername(String username) {
        return users.findByUsername(username).map(UserService::toSummary);
    }

    @Transactional(readOnly = true)
    public UserSummary get(UUID id) {
        return users.findById(id).map(UserService::toSummary).orElseThrow(() -> new NotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public List<UserSummary> list() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserService::toSummary).toList();
    }

    /**
     * Returns the user with this username, creating it if absent. Safe under concurrent callers: a lost insert race
     * is resolved by re-reading the winner's row.
     */
    public UserSummary ensureUser(@Valid NewUser request) {
        return findByUsername(request.username()).orElseGet(() -> {
            try {
                return createUser(request);
            } catch (DomainRuleViolationException raced) {
                return findByUsername(request.username()).orElseThrow(() -> raced);
            }
        });
    }

    /** True until the first account has a password: then the dashboard offers the one-time setup. */
    @Transactional(readOnly = true)
    public boolean setupRequired() {
        return !users.existsByPasswordHashIsNotNull();
    }

    /**
     * One-time setup of the first account, which becomes ADMIN. If the pre-login single-user account exists it is
     * taken over (renamed, password set), so everything already stored stays with it. Refused once any account can
     * sign in. Serialized in-process (single-instance deployment).
     */
    public synchronized UserSummary setUpFirstAccount(String username, String displayName, String password) {
        NewUser shape = NewUser.builder().username(username).displayName(displayName).build();
        validate(shape);
        Passwords.requireAcceptable(password, username);
        String hash = passwordEncoder.encode(password);
        try {
            return transactions.execute(status -> {
                if (users.existsByPasswordHashIsNotNull()) {
                    throw new DomainRuleViolationException("Setup is already complete; sign in instead");
                }
                AppUser user = users.findByUsername(properties.legacyUsername())
                        .orElseGet(() -> newEntity(username, displayName, null, UserRole.ADMIN));
                user.claim(username, displayName, UserRole.ADMIN);
                user.setPassword(hash, Instant.now(clock));
                return toSummary(users.saveAndFlush(user));
            });
        } catch (DataIntegrityViolationException e) {
            throw new DomainRuleViolationException("A user with this username already exists", e);
        }
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        transactions.executeWithoutResult(status -> {
            AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
            if (user.getPasswordHash() == null || currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new DomainRuleViolationException("The current password is not correct");
            }
            Passwords.requireAcceptable(newPassword, user.getUsername());
            user.setPassword(passwordEncoder.encode(newPassword), Instant.now(clock));
        });
    }

    /** An administrator sets a new password for someone who lost theirs. */
    public void resetPassword(UUID userId, String newPassword) {
        transactions.executeWithoutResult(status -> {
            AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
            Passwords.requireAcceptable(newPassword, user.getUsername());
            user.setPassword(passwordEncoder.encode(newPassword), Instant.now(clock));
        });
    }

    public UserSummary disable(UUID userId, UUID actingUserId) {
        if (userId.equals(actingUserId)) {
            throw new DomainRuleViolationException("You cannot disable your own account");
        }
        return transactions.execute(status -> {
            AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
            user.disable();
            return toSummary(user);
        });
    }

    private UserSummary save(AppUser user) {
        try {
            return transactions.execute(status -> toSummary(users.saveAndFlush(user)));
        } catch (DataIntegrityViolationException e) {
            throw new DomainRuleViolationException("A user with this username or email already exists", e);
        }
    }

    private void validate(NewUser shape) {
        if (shape.username() == null || !shape.username().matches(NewUser.USERNAME_REGEX)) {
            throw new DomainRuleViolationException("The username must be 2-64 chars: lower-case letters, digits, '.', '_' or '-'");
        }
        if (shape.displayName() == null || shape.displayName().isBlank() || shape.displayName().length() > 200) {
            throw new DomainRuleViolationException("A display name of at most 200 characters is required");
        }
    }

    private static AppUser newEntity(String username, String displayName, String email, UserRole role) {
        return AppUser.builder()
                .username(username)
                .displayName(displayName)
                .email(email == null ? null : email.toLowerCase(Locale.ROOT))
                .status(UserStatus.ACTIVE)
                .role(role)
                .build();
    }

    private static UserSummary toSummary(AppUser user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getStatus(),
                user.getRole(), user.getPasswordHash() != null);
    }
}
