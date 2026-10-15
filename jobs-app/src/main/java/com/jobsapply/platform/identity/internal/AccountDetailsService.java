package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads accounts for sign-in. Users without a password cannot sign in; locked-out usernames are reported locked. */
@Component
@RequiredArgsConstructor
class AccountDetailsService implements UserDetailsService {

    private final AppUserRepository users;
    private final LoginAttempts attempts;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username == null ? "" : username.strip())
                .filter(u -> u.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("unknown"));
        return new AccountPrincipal(user.getId(), user.getUsername(), user.getRole().name(), user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE, attempts.isLocked(user.getUsername()));
    }
}
