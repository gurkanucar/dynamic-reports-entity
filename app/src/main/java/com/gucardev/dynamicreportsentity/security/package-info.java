/**
 * Security setup. Demo: HTTP Basic with in-memory users. In production this would be an OAuth2
 * resource server (e.g. Keycloak) mapping token roles to the same authority names.
 */
@ApplicationModule(displayName = "Security")
package com.gucardev.dynamicreportsentity.security;

import org.springframework.modulith.ApplicationModule;
