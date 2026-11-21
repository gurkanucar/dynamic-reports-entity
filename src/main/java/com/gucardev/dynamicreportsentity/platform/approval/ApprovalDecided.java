package com.gucardev.dynamicreportsentity.platform.approval;

import java.util.UUID;
import java.time.Instant;

/**
 * A request got its final answer: approved or rejected by a member, or timed out. Published in the transaction
 * that decided it and delivered at least once (Spring Modulith's event publication registry): listeners must be
 * idempotent. Cancelled requests publish nothing; whoever cancelled already knows.
 *
 * @param sourceType what the request is for, as given to {@link ApprovalService#open} (e.g. {@code flow-step})
 * @param sourceId   which one (e.g. the step run id)
 * @param decidedBy  the member who decided; null for a timeout
 */
public record ApprovalDecided(UUID requestId, String sourceType, String sourceId, Outcome outcome, String decidedBy,
                              String comment, Instant decidedAt) {

    public enum Outcome { APPROVED, REJECTED, TIMED_OUT }
}
