package com.chimera.model;

/**
 * Approval lifecycle states for a ContentDraftRecord.
 *
 * <p>State machine (see specs/functional.md H-002, O-004):
 * <pre>
 *   PENDING_REVIEW ──► APPROVED ──► [PublicationAgent]
 *   PENDING_REVIEW ──► REJECTED ──► [Archived]
 *   PENDING_REVIEW ──► NEEDS_REVIEW (low confidence_score &lt; 0.6)
 *   NEEDS_REVIEW   ──► APPROVED | REJECTED
 *   Any status after 24h without decision ──► REJECTED (auto, H-003)
 * </pre>
 *
 * <p>IMMUTABLE CONSTRAINT: PublicationAgent throws IllegalStateException
 * for any status other than APPROVED (see specs/functional.md P-001).
 */
public enum DraftStatus {

    /** Draft created, awaiting human review. Default initial state. */
    PENDING_REVIEW,

    /** Draft flagged for extra attention — confidence_score &lt; 0.6. */
    NEEDS_REVIEW,

    /** Human approved. PublicationAgent may proceed. */
    APPROVED,

    /** Human rejected, or auto-rejected after 24h timeout. */
    REJECTED
}
