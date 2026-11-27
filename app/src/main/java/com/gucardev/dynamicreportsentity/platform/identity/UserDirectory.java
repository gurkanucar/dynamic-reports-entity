package com.gucardev.dynamicreportsentity.platform.identity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Looks users up by name with their current authorities (a flow acting as its owner, approval group members).
 * Backed by Spring Security's {@link UserDetailsService}, so LDAP or Keycloak can replace the demo users without
 * changing any module.
 */
@Component
public class UserDirectory {

    private final UserDetailsService users;

    UserDirectory(UserDetailsService users) {
        this.users = users;
    }

    /** @return empty if the user does not exist or may not log in (disabled, locked, expired) */
    public Optional<Actor> find(String username) {
        try {
            UserDetails user = users.loadUserByUsername(username);
            if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()) {
                return Optional.empty();
            }
            return Optional.of(new Actor(username, user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet())));
        } catch (UsernameNotFoundException e) {
            return Optional.empty();
        }
    }
}
