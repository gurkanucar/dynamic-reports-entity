package com.gucardev.dynamicreportsentity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Two ways in:
 * <ul>
 *   <li><b>Browser (the UI):</b> {@code POST /api/session} with username and password starts a session; the session
 *       cookie is HttpOnly (scripts never see a password or token) and shared by all tabs. Sessions live in the
 *       database (Spring Session JDBC), so every instance knows them. Requests on a session must carry the CSRF token
 *       ({@code XSRF-TOKEN} cookie → {@code X-XSRF-TOKEN} header).</li>
 *   <li><b>API clients (curl, integrations, tests):</b> HTTP Basic on every request, no session, no CSRF token
 *       (CSRF needs a browser that sends a cookie on its own).</li>
 * </ul>
 * In production both would sit behind the company login (e.g. Keycloak OIDC for the browser, bearer tokens for
 * clients), mapping roles to the same authority names.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/session").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                // Only requests that come with a session can be forged by another site; Basic-auth requests
                // carry their credentials explicitly and need no token.
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(SecurityConfiguration::hasNoSession))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // H2 console
                .build();
    }

    private static boolean hasNoSession(HttpServletRequest request) {
        return request.getSession(false) == null;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users) {
        return new ProviderManager(new DaoAuthenticationProvider(users));
    }

    /**
     * Demo users only (password: "password"); usernames match personnel.username in data.sql.
     * alice = HR, carol = Software Development, bob = IT with salary access, admin = everything.
     */
    @Bean
    UserDetailsService users() {
        return new InMemoryUserDetailsManager(
                user("alice", "REPORT_RUN"),
                user("carol", "REPORT_RUN"),
                user("bob", "REPORT_RUN", "SALARY_VIEW"),
                user("admin", "REPORT_RUN", "REPORT_ADMIN", "SALARY_VIEW", "NATIONAL_ID_VIEW", "ALL_DEPARTMENTS"));
    }

    private static UserDetails user(String name, String... authorities) {
        return User.withUsername(name).password("{noop}password").authorities(authorities).build();
    }
}
