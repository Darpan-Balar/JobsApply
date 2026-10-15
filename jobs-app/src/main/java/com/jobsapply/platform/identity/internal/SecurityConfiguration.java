package com.jobsapply.platform.identity.internal;

import com.jobsapply.platform.identity.UserService;
import com.jobsapply.platform.identity.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sign-in with a session cookie for the same-origin dashboard (ADR-0013). CSRF tokens use the SPA pattern (cookie
 * {@code XSRF-TOKEN}, header {@code X-XSRF-TOKEN}). Personal data is reachable only as the signed-in user; ADMIN
 * additionally changes the shared catalog and manages accounts.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
            + "connect-src 'self'; font-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttempts attempts, UserService users, JsonMapper json) {
        http
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/admin/**", "/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/postings/*/availability-check").authenticated()
                        // every other write changes the shared catalog
                        .requestMatchers("/api/v1/**").hasRole("ADMIN")
                        // the dashboard's files and routes; the dashboard itself asks for sign-in
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            attempts.succeeded(authentication.getName());
                            AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
                            UserSummary user = users.get(principal.userId());
                            writeJson(json, response, HttpStatus.OK, new SessionView(
                                    new SessionView.Me(user.id(), user.username(), user.displayName(), user.role()), false, false));
                        })
                        .failureHandler((request, response, exception) -> {
                            String username = request.getParameter("username");
                            if (exception instanceof LockedException) {
                                problem(json, response, HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-ins",
                                        "This account is temporarily locked; try again later");
                                return;
                            }
                            attempts.failed(username);
                            problem(json, response, HttpStatus.UNAUTHORIZED, "Sign-in failed", "Invalid username or password");
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("JOBS_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> problem(json, response, HttpStatus.UNAUTHORIZED,
                                "Unauthenticated", "Sign in first"))
                        .accessDeniedHandler((request, response, e) -> problem(json, response, HttpStatus.FORBIDDEN, "Forbidden",
                                "You are not allowed to do this (or the CSRF token is missing; reload the page)")))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new UserContextFilter(users), AuthorizationFilter.class);
        return http.build();
    }

    static void problem(JsonMapper json, HttpServletResponse response, HttpStatus status, String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        json.writeValue(response.getOutputStream(), Map.of("type", "about:blank", "title", title, "status", status.value(), "detail", detail));
    }

    private static void writeJson(JsonMapper json, HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), body);
    }

    /** True for requests from this machine (setup is offered only there). */
    public static boolean fromThisMachine(HttpServletRequest request) {
        try {
            return java.net.InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
