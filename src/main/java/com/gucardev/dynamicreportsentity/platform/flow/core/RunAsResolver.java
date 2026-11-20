package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Who a run acts as. Permissions are looked up when the step runs, not when the flow was saved: a flow owner
 * who loses a permission (or leaves) stops getting that data from their scheduled reports too.
 */
@Component
class RunAsResolver {

    private final UserDetailsService users;

    RunAsResolver(UserDetailsService users) {
        this.users = users;
    }

    Optional<ReportViewer> find(String username) {
        try {
            UserDetails user = users.loadUserByUsername(username);
            if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()) {
                return Optional.empty();
            }
            return Optional.of(new ReportViewer(username, user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet())));
        } catch (UsernameNotFoundException e) {
            return Optional.empty();
        }
    }

    ReportViewer resolve(String username) {
        return find(username).orElseThrow(() -> new StepException("RUN_AS_USER_UNAVAILABLE",
                "The flow owner '" + username + "' no longer exists or is disabled", false));
    }
}
