package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.identity.UserDirectory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Who a run acts as. Permissions are looked up when the step runs, not when the flow was saved: a flow owner
 * who loses a permission (or leaves) stops getting that data from their scheduled datasets too.
 */
@Component
public class RunAsResolver {

    private final UserDirectory users;

    RunAsResolver(UserDirectory users) {
        this.users = users;
    }

    public Optional<Actor> find(String username) {
        return users.find(username);
    }

    /** The run's user, or the step fails (no retry) if they no longer exist or are disabled. */
    public Actor resolve(String username) {
        return find(username).orElseThrow(() -> new StepException("RUN_AS_USER_UNAVAILABLE",
                "The flow owner '" + username + "' no longer exists or is disabled", false));
    }
}
