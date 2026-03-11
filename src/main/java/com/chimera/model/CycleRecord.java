package com.chimera.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO representing a single research-and-publish cycle.
 *
 * <p>Implements spec stories O-001 (Scheduled Trigger), O-002 (Manual Trigger),
 * O-005 (Cycle Completion). Matches GET /cycles/{id} response in specs/technical.md.
 *
 * <p>The stateVersion field is the OCC anchor — the Judge checks this value
 * before committing any Worker result. If stateVersion has advanced since a
 * Worker was forked, that Worker's output is discarded and re-queued.
 *
 * @param id          unique identifier
 * @param agentId     the InfluencerAgent running this cycle
 * @param triggerType how this cycle was started
 * @param status      current lifecycle status
 * @param startedAt   when the cycle began
 * @param completedAt when the cycle finished (null while RUNNING)
 * @param trendCount  number of trends discovered (0 until TrendResearch completes)
 * @param draftCount  number of drafts generated (0 until ContentGeneration completes)
 * @param stateVersion monotonically increasing version for OCC; incremented on every state change
 */
public record CycleRecord(
        UUID id,
        UUID agentId,
        TriggerType triggerType,
        String status,
        Instant startedAt,
        Instant completedAt,
        int trendCount,
        int draftCount,
        long stateVersion) {

    /** Cycle is actively running — Workers are executing. */
    public static final String STATUS_RUNNING = "RUNNING";

    /** All sub-agents completed successfully. */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** A sub-agent threw an unrecoverable exception. */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * Compact constructor — validates invariants.
     */
    public CycleRecord {
        if (id == null) {
            throw new IllegalArgumentException("CycleRecord.id must not be null");
        }
        if (agentId == null) {
            throw new IllegalArgumentException("CycleRecord.agentId must not be null");
        }
        if (triggerType == null) {
            throw new IllegalArgumentException("CycleRecord.triggerType must not be null");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("CycleRecord.status must not be null or blank");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("CycleRecord.startedAt must not be null");
        }
        if (stateVersion < 0) {
            throw new IllegalArgumentException("CycleRecord.stateVersion must not be negative");
        }
    }
}
