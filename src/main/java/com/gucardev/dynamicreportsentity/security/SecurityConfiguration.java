package com.gucardev.dynamicreportsentity.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)                          // stateless API
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // H2 console
                .build();
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
