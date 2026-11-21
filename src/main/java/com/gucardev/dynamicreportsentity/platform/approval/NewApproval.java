package com.gucardev.dynamicreportsentity.platform.approval;

import java.time.Instant;

/**
 * What another module asks approval for.
 *
 * @param sourceType        what kind of thing waits (e.g. {@code flow-step}); with {@code sourceId} it identifies the
 *                          request, so opening it twice returns the first one
 * @param subject           what it is about, shown to approvers (e.g. the flow's name)
 * @param link              optional path in the UI with more context (e.g. the run)
 * @param requestedBy       who asked (may not approve unless {@code allowSelfApproval})
 * @param onBehalfOf        who the work runs for, e.g. the flow owner (may not approve either)
 * @param notifyMembers     e-mail the group members that have an address
 */
public record NewApproval(String sourceType, String sourceId, String subject, String link, long groupId, String title,
                          String description, String requestedBy, String onBehalfOf, boolean allowSelfApproval,
                          boolean notifyMembers, Instant dueAt) {
}
