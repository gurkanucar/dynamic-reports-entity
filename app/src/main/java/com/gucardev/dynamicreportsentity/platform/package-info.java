/**
 * Shared capabilities that business modules build on, as opposed to the business features themselves
 * (organization, personnel, training, project). Not a module of its own: each sub-package is one
 * ({@code flow}, {@code storage}, {@code survey}...), found through {@code @ApplicationModule}
 * ({@code spring.modulith.detection-strategy=explicitly-annotated}).
 * <p>
 * Platform modules never depend on business modules; business modules plug into them. Reporting itself is the
 * separate {@code entity-reports} library (Maven module next to this app): business modules annotate their entities
 * and provide row policies for it.
 */
package com.gucardev.dynamicreportsentity.platform;
