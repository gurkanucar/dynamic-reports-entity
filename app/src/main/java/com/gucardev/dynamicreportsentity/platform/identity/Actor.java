package com.gucardev.dynamicreportsentity.platform.identity;

import com.gucardev.entityreports.Dataset;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who does something: a logged-in user, a user a flow acts as, or the engine itself. Every module asks this for
 * permissions instead of depending on Spring Security or on each other. It is also a reporting
 * {@link Dataset.Viewer}, so an Actor runs datasets as it is.
 *
 * @param system the engine itself (background jobs): every authority, no row restrictions. Never created
 *               from a request; see {@link #system(String)}.
 */
public record Actor(String username, Set<String> authorities, boolean system) implements Dataset.Viewer {

    public Actor {
        authorities = Set.copyOf(authorities);
    }

    public Actor(String username, Set<String> authorities) {
        this(username, authorities, false);
    }

    /** The engine working on its own: sees everything, so its results must be guarded when they are read. */
    public static Actor system(String purpose) {
        return new Actor("system:" + purpose, Set.of(), true);
    }

    /** The caller of a web request. */
    public static Actor of(Authentication authentication) {
        return new Actor(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }

    @Override
    public boolean hasAuthority(String authority) {
        return system || authorities.contains(authority);
    }

    /** The engine: every field, no row restrictions in datasets. */
    @Override
    public boolean unrestricted() {
        return system;
    }
}
