package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserStatus;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.tenancy.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the signed-in user to {@link UserContext} for the rest of the request, so every personal query is scoped to
 * them. A session whose account was disabled since sign-in is ended here.
 */
class UserContextFilter extends OncePerRequestFilter {

    private final UserService users;

    UserContextFilter(UserService users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        if (!active(principal)) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            UserContext.callAsChecked(principal.userId(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private boolean active(AccountPrincipal principal) {
        try {
            return users.get(principal.userId()).status() == UserStatus.ACTIVE;
        } catch (NotFoundException e) {
            return false;
        }
    }
}
