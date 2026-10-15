package com.jobsapply.platform.identity.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The signed-in account. Carries the user id that {@code UserContext} is bound to; never the password after sign-in. */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String username;
    private final String role;
    private final boolean enabled;
    private final boolean locked;
    private String passwordHash;

    AccountPrincipal(UUID userId, String username, String role, String passwordHash, boolean enabled, boolean locked) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.locked = locked;
    }

    public UUID userId() {
        return userId;
    }

    public String role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Called by Spring Security after authentication. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "AccountPrincipal[" + userId + "]";
    }
}
