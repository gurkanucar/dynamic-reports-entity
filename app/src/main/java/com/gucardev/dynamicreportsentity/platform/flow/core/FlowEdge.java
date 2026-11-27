package com.gucardev.dynamicreportsentity.platform.flow.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A connection from one step to the next.
 *
 * @param when the outcome of {@code from} this edge is taken on ({@code true}, {@code approved} ...); null = any
 *             outcome without an edge of its own
 */
public record FlowEdge(@NotBlank @Size(max = 40) String from, @NotBlank @Size(max = 40) String to,
                       @Size(max = 20) String when) {
}
