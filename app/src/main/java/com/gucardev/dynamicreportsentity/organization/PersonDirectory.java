package com.gucardev.dynamicreportsentity.organization;

import java.util.Collection;
import java.util.Map;

/**
 * Names of people, for a department's manager; implemented by the personnel module (which depends on this one, so
 * the organization module never depends on personnel).
 */
public interface PersonDirectory {

    Map<Long, String> names(Collection<Long> personIds);
}
