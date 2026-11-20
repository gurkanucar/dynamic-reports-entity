/**
 * Shared capabilities that business modules build on, as opposed to the business features themselves
 * (organization, personnel, training, project). Not a module of its own: each sub-package is one
 * ({@code reporting}, {@code flow}, {@code storage}), found through {@code @ApplicationModule}
 * ({@code spring.modulith.detection-strategy=explicitly-annotated}).
 * <p>
 * Platform modules never depend on business modules; business modules plug into them (e.g. reporting
 * annotations, row policies).
 */
package com.gucardev.dynamicreportsentity.platform;
