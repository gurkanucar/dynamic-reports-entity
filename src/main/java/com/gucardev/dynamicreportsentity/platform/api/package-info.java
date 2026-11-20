/**
 * The shape every platform HTTP API shares (docs/REST API Tasarım Rehberi.md): the {@code {data, meta}} success
 * envelope and RFC 9457 problems with {@code code} and {@code traceId}. Depends on nothing else in the application.
 */
@ApplicationModule(id = "api", displayName = "API conventions")
package com.gucardev.dynamicreportsentity.platform.api;

import org.springframework.modulith.ApplicationModule;
