package com.chimera.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable DTO representing a content draft produced by ContentGenerationAgent.
 *
 * <p>Implements spec stories C-001 (Generate ContentDraft) and C-003 (Confidence Scoring).
 * Matches the API response shape in specs/technical.md GET /drafts/{id}.
 *
 * <p>Key OCC constraint: the Judge must verify that the CycleRecord's state_version
 * has not advanced since this draft was generated before committing it to the database.
 * Because this is a Record (immutable), a stale draft cannot be silently mutated by
 * a concurrent Worker — the Judge will always see the original snapshot.
 *
 * @param id               unique identifier
 * @param agentId          the InfluencerAgent that generated this draft
 * @param trendId          the TrendRecord that inspired this draft
 * @param title            video title
 * @param script           video script (max 500 words, enforced by ContentGenerationAgent)
 * @param hashtags         platform hashtags (max 30 entries)
 * @param platformMetadata platform-specific fields stored as JSONB in PostgreSQL
 * @param status           current approval status (starts as PENDING_REVIEW)
 * @param confidenceScore  self-evaluation score [0.0, 1.0]; below 0.6 → NEEDS_REVIEW
 * @param reviewNotes      operator notes on approval/rejection (null until reviewed)
 * @param createdAt        when this draft was created
 * @param approvedAt       when this draft was approved (null until APPROVED)
 */
public record ContentDraftRecord(
        UUID id,
        UUID agentId,
        UUID trendId,
        String title,
        String script,
        List<String> hashtags,
        Map<String, Object> platformMetadata,
        DraftStatus status,
        float confidenceScore,
        String reviewNotes,
        Instant createdAt,
        Instant approvedAt) {

    /** Confidence threshold below which drafts are flagged NEEDS_REVIEW (C-003). */
    public static final float LOW_CONFIDENCE_THRESHOLD = 0.6f;

    /**
     * Compact constructor — validates invariants.
     */
    public ContentDraftRecord {
        if (id == null) {
            throw new IllegalArgumentException("ContentDraftRecord.id must not be null");
        }
        if (agentId == null) {
            throw new IllegalArgumentException("ContentDraftRecord.agentId must not be null");
        }
        if (trendId == null) {
            throw new IllegalArgumentException("ContentDraftRecord.trendId must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("ContentDraftRecord.title must not be null or blank");
        }
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("ContentDraftRecord.script must not be null or blank");
        }
        if (hashtags == null) {
            throw new IllegalArgumentException("ContentDraftRecord.hashtags must not be null");
        }
        if (hashtags.size() > 30) {
            throw new IllegalArgumentException("ContentDraftRecord.hashtags must not exceed 30 entries");
        }
        if (platformMetadata == null) {
            throw new IllegalArgumentException("ContentDraftRecord.platformMetadata must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("ContentDraftRecord.status must not be null");
        }
        if (confidenceScore < 0.0f || confidenceScore > 1.0f) {
            throw new IllegalArgumentException("ContentDraftRecord.confidenceScore must be in [0.0, 1.0]");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("ContentDraftRecord.createdAt must not be null");
        }
        // Defensive copy to prevent external mutation of the list/map
        hashtags = List.copyOf(hashtags);
        platformMetadata = Map.copyOf(platformMetadata);
    }

    /**
     * Returns true if this draft's confidence is below the low-confidence threshold (C-003).
     *
     * @return true when confidenceScore &lt; LOW_CONFIDENCE_THRESHOLD
     */
    public boolean isLowConfidence() {
        return confidenceScore < LOW_CONFIDENCE_THRESHOLD;
    }
}
