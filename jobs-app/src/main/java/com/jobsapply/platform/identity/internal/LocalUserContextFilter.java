package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserSummary;
import com.jobsapply.platform.shared.tenancy.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * LOCAL identity mode: binds the configured local user to {@link UserContext} for each API request. This is not
 * authentication — it is only safe because the server binds to localhost. Replaced by real login in Phase 8.
 */
class LocalUserContextFilter extends OncePerRequestFilter {

    private final UserService users;
    private final String username;
    private final AtomicReference<UUID> userId = new AtomicReference<>();

    LocalUserContextFilter(UserService users, String username) {
        this.users = users;
        this.username = username;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            UserContext.callAsChecked(localUserId(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private UUID localUserId() {
        UUID id = userId.get();
        if (id == null) {
            id = users.findByUsername(username).map(UserSummary::id)
                    .orElseThrow(() -> new IllegalStateException("Local user '" + username + "' does not exist"));
            userId.set(id);
        }
        return id;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "jobs.identity.mode", havingValue = "LOCAL", matchIfMissing = true)
    static class Registration {

        @Bean
        FilterRegistrationBean<LocalUserContextFilter> localUserContextFilter(UserService users, IdentityProperties properties) {
            FilterRegistrationBean<LocalUserContextFilter> registration =
                    new FilterRegistrationBean<>(new LocalUserContextFilter(users, properties.localUser().username()));
            registration.addUrlPatterns("/api/*");
            return registration;
        }
    }
}
