package com.jobsapply.platform.identity;

import com.jobsapply.platform.identity.internal.AppUser;
import com.jobsapply.platform.identity.internal.AppUserRepository;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

/** Public API of the identity module. */
@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository users;
    private final TransactionTemplate transactions;

    /** Creates a user. Fails if the username or email is already taken. */
    public UserSummary createUser(@Valid NewUser request) {
        try {
            return transactions.execute(status -> toSummary(users.saveAndFlush(newEntity(request))));
        } catch (DataIntegrityViolationException e) {
            throw new DomainRuleViolationException("A user with this username or email already exists", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserSummary> findByUsername(String username) {
        return users.findByUsername(username).map(UserService::toSummary);
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

    private static AppUser newEntity(NewUser request) {
        return AppUser.builder()
                .username(request.username())
                .displayName(request.displayName())
                .email(request.email() == null ? null : request.email().toLowerCase(Locale.ROOT))
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static UserSummary toSummary(AppUser user) {
        return new UserSummary(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getStatus());
    }
}
