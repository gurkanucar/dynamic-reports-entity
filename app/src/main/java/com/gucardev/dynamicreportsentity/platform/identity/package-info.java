/**
 * Identity module: who is acting ({@link com.gucardev.dynamicreportsentity.platform.identity.Actor}) and finding
 * users ({@link com.gucardev.dynamicreportsentity.platform.identity.UserDirectory}). Depends on nothing but
 * Spring Security; every other platform module depends on it instead of on each other.
 */
@ApplicationModule(id = "identity", displayName = "Identity")
package com.gucardev.dynamicreportsentity.platform.identity;

import org.springframework.modulith.ApplicationModule;
